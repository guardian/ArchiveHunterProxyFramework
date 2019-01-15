#!/bin/bash

#expects arguments:  extract_thumbnail.sh {s3-uri-of-source} {s3-bucket-for-proxies} {sns-output-topic} {job-id}
echo extract_video_thumbnail starting. Arguments: $1 $2 $3 $4

if [ "$4" == "" ]; then
    echo You need to pass a job ID!
    exit 1
fi

MIMETYPE=$(file -b --mime-type /tmp/videofile)
echo $MIMETYPE | grep video

if [ "$?" != "0" ]; then
    echo This is not a video file.
    exit 1
fi

if [ "$FRAME_LOCATION" == "" ]; then
    FRAME_LOCATION=00:00:10
fi

if [ "$OUTPUT_QUALITY" == "" ]; then
    OUTPUT_QUALITY=2
fi

echo Extracting thumbnail...
echo ffmpeg -i /tmp/videofile -ss ${FRAME_LOCATION} -vframes 1 -q:v ${OUTPUT_QUALITY} /tmp/output.jpg


OUTLOG=$(ffmpeg -i /tmp/videofile -ss ${FRAME_LOCATION} -vframes 1 -q:v ${OUTPUT_QUALITY} -y /tmp/output.jpg 2>&1)

FFMPEG_EXIT=$?
echo $OUTLOG

INPATH=$(echo "$1" | sed 's/s3:\/\/[^\/]*\///')
echo inpath is $INPATH
INPATH_NO_EXT=$(echo "$INPATH" | sed -E 's/\.[^\.]+$//')
echo inpath no ext is $INPATH_NO_EXT
OUTPATH="s3://$2/$INPATH_NO_EXT.jpg"
echo outpath is $OUTPATH

if [ "$FFMPEG_EXIT" == "0" ]; then
    echo Uploading thumbnail...
    UPLOAD_LOG=`aws s3 cp /tmp/output.jpg "$OUTPATH" 2>&1`
    echo Server callback URL is $3

    if [ "$?" == "0" ]; then
        echo Informing server...
        aws sns publish --topic-arn $3 --message '{"status":"success","output":"'"$OUTPATH"'","jobId":"'"$4"'","input":"'"$1"'"}'
    else
        echo Informing server of failure...
        ENCODED_LOG=$(echo $UPLOAD_LOG | base64)

        aws sns publish --topic-arn $3 --message '{"status":"error","log":"'$ENCODED_LOG'","jobId":"'"$4"'","input":"'"$1"'"}'
    fi
else
    echo Output failed. Informing server...
    echo Server callback URL is $3
    ENCODED_LOG=$(echo $OUTLOG | base64)
    aws sns publish --topic-arn $3 --message '{"status":"error","log":"'$ENCODED_LOG'","jobId":"'"$4"'","input":"'"$1"'"}'
fi