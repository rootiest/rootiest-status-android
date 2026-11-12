#!/usr/bin/env python3
"""play-upload.py: upload app bundles to Google Play and release them on a testing track.

  play-upload.py --package PKG [--key FILE] [--notes FILE] BUNDLE[=TRACK] ...
  play-upload.py --package PKG [--key FILE] --check

Each BUNDLE goes to TRACK (default: internal; the watch's Wear OS track is wear:internal), as one release per track
with every version code uploaded for it, rolled out to all of that track's testers. Everything happens in one Play
edit, so either all of it is published or none of it. --notes is a text file of release notes (en-US, cut to Play's
500 characters). --check signs in and lists the tracks with their latest release, changing nothing.

The key is a service account's JSON key with access to the app in Play Console (docs/README.md, "Google Play");
--key defaults to $PLAY_KEY, then ~/.local/share/android/play-publisher.json. Needs only Python's standard library
and the openssl command, which signs the sign-in request.
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import NoReturn

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
DEFAULT_KEY = os.path.expanduser("~/.local/share/android/play-publisher.json")
NOTES_LIMIT = 500


def die(message) -> NoReturn:
    sys.exit(f"play-upload: {message}")


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def access_token(key):
    """A service-account sign-in: a JWT signed with the key's RSA private key, traded for an access token."""
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT", "kid": key.get("private_key_id", "")}
    claims = {"iss": key["client_email"], "scope": SCOPE, "aud": key["token_uri"], "iat": now, "exp": now + 3600}
    signing_input = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(claims).encode())}"
    # openssl reads the private key from a file: a private temporary directory, removed straight after
    with tempfile.TemporaryDirectory() as tmp:
        pem = os.path.join(tmp, "key.pem")
        with open(os.open(pem, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as f:
            f.write(key["private_key"])
        signature = subprocess.run(["openssl", "dgst", "-sha256", "-sign", pem], input=signing_input.encode(),
                                   capture_output=True, check=True).stdout
    body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                   "assertion": f"{signing_input}.{b64url(signature)}"}).encode()
    return call("POST", key["token_uri"], body, {"Content-Type": "application/x-www-form-urlencoded"})["access_token"]


def call(method, url, body=None, headers=None, token=None):
    headers = dict(headers or {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if isinstance(body, (dict, list)):
        body = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            text = response.read()
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        try:
            detail = json.loads(detail)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            pass
        raise ApiError(e.code, detail) from None
    return json.loads(text) if text else {}


class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(f"HTTP {status}: {message}")
        self.message = message


def play_notes(path):
    if not path:
        return None
    text = open(path).read().strip()
    if len(text) <= NOTES_LIMIT:
        return text or None
    # keep whole lines, as many as fit with the marker
    kept = ""
    for line in text.splitlines():
        if len(kept) + len(line) + 3 > NOTES_LIMIT:
            break
        kept += line + "\n"
    return kept + "…"


def main():
    parser = argparse.ArgumentParser(description=(__doc__ or "").split("\n\n")[0])
    parser.add_argument("--package", required=True)
    parser.add_argument("--key", default=os.environ.get("PLAY_KEY", DEFAULT_KEY))
    parser.add_argument("--notes")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("bundles", nargs="*", metavar="BUNDLE[=TRACK]")
    args = parser.parse_args()
    if not args.check and not args.bundles:
        die("no bundles to upload (or --check)")
    try:
        key = json.load(open(args.key))
    except OSError as e:
        die(f"cannot read the key {args.key}: {e.strerror}")

    uploads = []
    for spec in args.bundles:
        path, _, track = spec.partition("=")
        if not os.path.isfile(path):
            die(f"{path}: no such bundle")
        uploads.append((path, track or "internal"))

    app = f"{API}/{args.package}"
    try:
        token = access_token(key)
        edit = call("POST", f"{app}/edits", {}, token=token)["id"]
    except ApiError as e:
        die(f"{args.package}: {e}")

    try:
        if args.check:
            for track in call("GET", f"{app}/edits/{edit}/tracks", token=token).get("tracks", []):
                releases = track.get("releases", [])
                latest = releases[0] if releases else {}
                print(f"{args.package} {track['track']}: {latest.get('name', 'no release')} "
                      f"{latest.get('status', '')}".rstrip())
            call("DELETE", f"{app}/edits/{edit}", token=token)
            return

        tracks = {}
        for path, track in uploads:
            print(f"play-upload: uploading {os.path.basename(path)} ({os.path.getsize(path) // 1024} KiB)")
            data = open(path, "rb").read()
            bundle = call("POST", f"{UPLOAD}/{args.package}/edits/{edit}/bundles?uploadType=media", data,
                          {"Content-Type": "application/octet-stream"}, token)
            if bundle.get("sha256") and bundle["sha256"] != hashlib.sha256(data).hexdigest():
                raise ApiError(0, f"{path}: Play received different bytes than were sent")
            tracks.setdefault(track, []).append(str(bundle["versionCode"]))

        notes = play_notes(args.notes)
        for track, codes in tracks.items():
            release = {"versionCodes": codes, "status": "completed"}
            if notes:
                release["releaseNotes"] = [{"language": "en-US", "text": notes}]
            call("PUT", f"{app}/edits/{edit}/tracks/{track}", {"track": track, "releases": [release]}, token=token)

        try:
            call("POST", f"{app}/edits/{edit}:commit", token=token)
        except ApiError as e:
            # Play refuses to send an edit for review while the app has other changes that cannot be sent yet
            # (such as an unfinished store listing); committing without sending leaves the review to Play Console.
            if "changesNotSentForReview" not in e.message:
                raise
            call("POST", f"{app}/edits/{edit}:commit?changesNotSentForReview=true", token=token)
            print("play-upload: committed without sending for review: check Publishing overview in Play Console")
        for track, codes in tracks.items():
            print(f"play-upload: {args.package} {track}: released version {', '.join(codes)}")
    except ApiError as e:
        try:
            call("DELETE", f"{app}/edits/{edit}", token=token)
        except ApiError:
            pass
        die(f"{args.package}: {e} (nothing was published)")


if __name__ == "__main__":
    main()
