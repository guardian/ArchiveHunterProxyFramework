#!/usr/bin/env bash

#expects arguments:  extract_thumbnail.sh {s3-uri-of-source} {s3-bucket-for-proxies} {sns-update-topic} {job-id}
echo extract_thumbnail starting. Arguments: $1 $2 $3 $4

if [ "$1" == "" ]; then
    echo "You must specific a source S3 URI"
    exit 1
fi

if [ "$2" == "" ]; then
    echo "You must specify a destination bucket"
    exit 1
fi

if [ "$3" == "" ]; then
    echo "You must specify a callback URL"
    exit 1
fi

if [ "$4" == "" ]; then
    echo "You must specify a job ID"
    exit 1
fi

echo Downloading source media $1...
aws s3 cp "$1" /tmp/mediafile
if [ "$?" != "0" ]; then
    aws sns publish --topic-arn $3 --message '{"status":"FAILURE","log":"Could not download source media","jobId":"'"$4"'","input":"'"$1"'"}'
    echo Could not download source media.
    exit 1
fi

MIMETYPE=$(file -b --mime-type /tmp/mediafile)

LASTFOUR="${1: -4}"
declare -l LASTFOUR
LASTFOUR=$LASTFOUR

if [[ "$MIMETYPE" =~ ^audio.* ]]; then
    echo Got audio file
    mv /tmp/mediafile /tmp/audiofile
    extract_audio_thumbnail.sh "$1" "$2" "$3" "$4"
    exit $?
elif [[ "$MIMETYPE" =~ ^video.* ]]; then
    echo Got video file
    mv /tmp/mediafile /tmp/videofile
    extract_video_thumbnail.sh "$1" "$2" "$3" "$4"
    exit $?
elif [[ "$MIMETYPE" =~ ^image/x-canon-cr2.* ]] || [[ ( "$LASTFOUR" == ".nef" ) && ( "$MIMETYPE" =~ ^image/tiff.* ) ]]; then
    echo Got camera raw image file
    mv /tmp/mediafile /tmp/imagefile
    extract_raw_thumbnail.sh "$1" "$2" "$3" "$4"
    exit $?
elif [[ "$MIMETYPE" =~ ^image.* ]]; then
    echo Got image file
    mv /tmp/mediafile /tmp/imagefile
    extract_image_thumbnail.sh "$1" "$2" "$3" "$4"
    exit $?
elif [[ "$MIMETYPE" == "application/octet-stream" ]]; then
    #if we don't know, assume video
    echo Assuming video for application/octet-stream
    mv /tmp/mediafile /tmp/videofile
    extract_video_thumbnail.sh "$1" "$2" "$3" "$4"
    exit $?
else
    echo ${MIMETYPE} files are not supported yet
    echo Script failed. Informing server...
    echo Server callback URL is $3
    aws sns publish --topic-arn $3 --message '{"status":"FAILURE","log":"MIME type of file ('$MIMETYPE') not supported.","jobId":"'"$4"'","input":"'"$1"'"}'
    exit 1
fi
