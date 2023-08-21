FROM alpine:latest

# Update and Install packages
RUN apk update && \
    apk add --no-cache gettext-dev \
    ffmpeg python3 python3-dev py3-pip curl file imagemagick sox gnuplot jasper-dev jpeg-dev lcms2-dev gcc g++ make bash musl-dev wget && \
    pip3 install --upgrade pip

# Set library path
ENV LD_LIBRARY_PATH /usr/local/lib:$LD_LIBRARY_PATH

# Install dcraw
RUN wget https://www.dechifro.org/dcraw/archive/dcraw-9.28.0.tar.gz && \
    tar xvzf dcraw-9.28.0.tar.gz && \
    cd dcraw && bash ./install && \
    gcc -o dcraw -O4 dcraw.c -lm -ljasper -ljpeg -llcms2 -L/usr/lib -lintl && \
    cp /dcraw/dcraw /usr/local/bin/dcraw && \
    chmod a+x /usr/local/bin/dcraw

# Remove unnecessary packages and files
RUN apk del wget gcc g++ make && \
    rm -rf /var/cache/apk/*

# Install python packages
RUN pip3 install awscli requests boto3

# Copy scripts and set permissions
COPY scripts/* /usr/local/bin/
RUN chmod a+x /usr/local/bin/extract_*.sh

# Set user
RUN adduser -D proxy
USER proxy
