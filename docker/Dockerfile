FROM centos:7

RUN yum -y clean all && yum -y update && yum -y install epel-release \
    && yum -y install ffmpeg python3 python3-pip curl file ImageMagick sox gnuplot && yum -y clean all && rm -rf /var/cache/yum
RUN pip3 install awscli requests boto3 && mkdir -p /usr/local/share/thumbnailer
COPY scripts/extract_video_thumbnail.sh /usr/local/bin/extract_video_thumbnail.sh
COPY scripts/extract_audio_thumbnail.sh /usr/local/bin/extract_audio_thumbnail.sh
COPY scripts/extract_image_thumbnail.sh /usr/local/bin/extract_image_thumbnail.sh
COPY scripts/extract_raw_thumbnail.sh /usr/local/bin/extract_raw_thumbnail.sh
COPY scripts/analyze_media_file.py /usr/local/bin/analyze_media_file.py
COPY scripts/audio.gpi /usr/local/share/thumbnailer/audio.gpi
COPY scripts/extract_thumbnail.sh /usr/local/bin/extract_thumbnail.sh
RUN chmod a+x /usr/local/bin/extract_*.sh
COPY docker_install.sh /root
RUN chmod a+x /root/docker_install.sh
RUN /root/docker_install.sh
RUN cp /dcraw/dcraw /usr/local/bin/dcraw
RUN chmod a+x /usr/local/bin/dcraw
RUN useradd proxy
USER proxy
