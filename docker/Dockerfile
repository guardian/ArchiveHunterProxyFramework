FROM rockylinux/rockylinux:8

# Enable repos
RUN dnf -y clean all && \
    dnf -y update && \
    dnf -y install https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm && \
    dnf -y install https://download1.rpmfusion.org/free/el/rpmfusion-free-release-8.noarch.rpm https://download1.rpmfusion.org/nonfree/el/rpmfusion-nonfree-release-8.noarch.rpm && \
    dnf -y install epel-release dnf-plugins-core && \
    dnf config-manager --set-enabled powertools

# Install Python 3.8
RUN dnf -y install python38 python38-pip

RUN dnf -y install SDL2 SDL2-devel ffmpeg curl file ImageMagick sox gnuplot jasper-devel libjpeg-devel lcms2-devel wget && \
    dnf -y groupinstall 'Development Tools'

# Set library path
ENV LD_LIBRARY_PATH /usr/local/lib:$LD_LIBRARY_PATH

# Install dcraw
RUN wget https://www.dechifro.org/dcraw/archive/dcraw-9.28.0.tar.gz
RUN tar xvzf dcraw-9.28.0.tar.gz
RUN cd dcraw && bash ./install
RUN cd dcraw && gcc -o dcraw -O4 dcraw.c -lm -ljasper -ljpeg -llcms2
RUN cp /dcraw/dcraw /usr/local/bin/dcraw
RUN chmod a+x /usr/local/bin/dcraw

# Remove unnecessary packages and files
RUN dnf -y remove wget && \
    dnf -y groupremove 'Development Tools' && \
    dnf -y clean all && rm -rf /var/cache/dnf

# Install python packages
RUN pip3.8 install awscli requests boto3

# Copy scripts and set permissions
COPY scripts/* /usr/local/bin/
RUN chmod a+x /usr/local/bin/extract_*.sh

# Set user
RUN useradd proxy
USER proxy
