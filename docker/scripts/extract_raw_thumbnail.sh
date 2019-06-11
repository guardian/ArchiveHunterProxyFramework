#!/bin/bash

#expects arguments:  extract_raw_thumbnail.sh {s3-uri-of-source} {s3-bucket-for-proxies} {sns-update-topic} {job-id}

echo Shrinking image...
/dcraw/dcraw -c /tmp/imagefile | convert - -resize 640x360 -define modulate:colorspace=HSB -modulate 106,90,96\> /tmp/resized.jpg > /tmp/logfile 2>&1
CONVERT_EXIT=$?
cat /tmp/logfile

if [ "$CONVERT_EXIT" == "0" ]; then
    echo Uploading thumbnail...
    INPATH=$(echo "$1" | sed 's/s3:\/\/[^\/]*\///')
    echo inpath is $INPATH
    INPATH_NO_EXT=$(echo "$INPATH" | sed -E 's/\.[^\.]+$//')
    echo inpath no ext is $INPATH_NO_EXT
    OUTPATH="s3://$2/${INPATH_NO_EXT}_thumb.jpg"
    echo outpath is $OUTPATH

    UPLOAD_LOG=`aws s3 cp /tmp/resized.jpg "$OUTPATH" 2>&1`
    echo Server callback URL is $3

    if [ "$?" == "0" ]; then
        echo Informing server...
        aws sns publish --topic-arn $3 --message '{"status":"SUCCESS","output":"'"$OUTPATH"'","jobId":"'"$4"'","input":"'"$1"'"}'
    else
        echo Informing server of failure...
        ENCODED_LOG=$(echo $UPLOAD_LOG | base64)

        aws sns publish --topic-arn $3 --message '{"status":"FAILURE","log":"'$ENCODED_LOG'","jobId":"'"$4"'","input":"'"$1"'"}'
    fi
else
    echo Output failed. Informing server...
    echo Server callback URL is $3
    ENCODED_LOG=$(base64 /tmp/logfile)
    aws sns publish --topic-arn $3 --message '{"status":"FAILURE","log":"'$ENCODED_LOG'","jobId":"'"$4"'","input":"'"$1"'"}'
fi
