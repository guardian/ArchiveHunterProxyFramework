#!/usr/bin/env python

#expects arguments:  analyze_media_file.py {s3-uri-of-source} {http-uri-archivehunter}

import requests
import json
import sys
import boto3
from pprint import pprint
import time
import traceback
import subprocess
from urlparse import urlparse


def s3_download(uri, local_path):
    """
    download the given S3 URI to the file pointed to by local_path
    :param uri: s3:// URI to download. ValueError is raised if this is not a valid S3 URI pointing to a file.
    :param local_path: path to download to. This should include the filename
    :return: None. Raises if the download fails.
    """
    uridata = urlparse(uri)
    if uridata.scheme != "s3":
        raise ValueError("You must specify an s3:// URL")
    if uridata.path == "":
        raise ValueError("No path to download")

    print "Downloading {0} from {1}".format(uridata.path.lstrip("/"),uridata.netloc)
    svc = boto3.resource("s3")
    svc.Bucket(uridata.netloc).download_file(uridata.path.lstrip("/"), local_path)


def call_ffprobe(path):
    """
    calls ffprobe on the given path. returns a dictionary of information from ffprobe
    :param path: media path to examine
    :return: dictionary of metadata
    """
    proc = subprocess.Popen(args=["/usr/bin/ffprobe","-of","json","-show_streams","-show_format","-show_data",path],stdout=subprocess.PIPE,stderr=subprocess.PIPE)

    (stdout,stderr) = proc.communicate()
    if proc.returncode != 0:
        print "FFprobe failed: "
        print stderr
        report_error(callback_uri, stderr)
    else:
        return json.loads(stdout)   #an exception from this gets caught at root level


def send_with_retry(callback_uri, content, attempt=0):
    print content

    result = requests.post(callback_uri, data=content, headers={'Content-Type': "application/json"})
    if result.status_code != 200:
        print "WARNING: server returned {0}".format(result.status_code)
        time.sleep(10)
        send_with_retry(callback_uri, content, attempt+1)


def report_success(meta_dict):
    content = json.dumps({
        "status": "success",
        "input": download_uri,
        "metadata": meta_dict
    })

    send_with_retry(callback_uri, content)


def report_error(callback_uri, description, attempt=0):
    if sys.exc_info() == (None,None,None):
        log = description
    else:
        log = description + "\n" + traceback.format_exc()

    content = json.dumps({
        "status": "error",
        "input": download_uri,
        "log": log
    })
    print "Logging error to server at {0}: {1}".format(callback_uri, content)
    send_with_retry(callback_uri, content)


#START MAIN
download_uri = sys.argv[1]
callback_uri = sys.argv[2]

try:
    print "Downloading from {0}".format(download_uri)
    print "Callback URI is {0}".format(callback_uri)
    s3_download(download_uri, "/tmp/mediafile")
    metadata = call_ffprobe("/tmp/mediafile")
    report_success(metadata)
except Exception as e:
    report_error(callback_uri, "")