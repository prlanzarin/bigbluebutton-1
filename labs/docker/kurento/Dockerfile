FROM ubuntu:18.04

ARG APT_KEY="https://ubuntu.bigbluebutton.org/repo/bigbluebutton.asc"
ARG APT_REPO="deb https://ubuntu.bigbluebutton.org/bionic-24 bigbluebutton-bionic main"
ARG CACHE_BUST=1

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update \
 && apt-get -y dist-upgrade \
 && apt-get install -y software-properties-common curl wget apt-transport-https

RUN wget "$APT_KEY" -O- | apt-key add - \
 && add-apt-repository "$APT_REPO" \
 && apt-get update \
 && apt-get -y install kurento-media-server bzip2 jq \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

RUN apt-get update \
 && apt-get install -y --download-only openh264-gst-plugins-bad-1.5

COPY ./docker-entrypoint.sh /usr/local/bin/
COPY ./healthchecker.sh /healthchecker.sh

ENV PORT=8888
ENV RTP_MIN_PORT=32769
ENV RTP_MAX_PORT=34769
ENV KURENTO_LOGS_PATH=/var/log/kurento-media-server
ENV GST_DEBUG=Kurento*:3
ENV RECORDER_GAPS_FIX=NONE
ENV NICE_AGENT_ICE_TCP=0
ENV G_MESSAGES_DEBUG=""
ENV STUN_IP=""
ENV STUN_PORT=""
ENV TURN_URL=""
ENV NETWORK_INTERFACES=""
ENV PEM_CERTIFICATE_RSA=""
ENV PEM_CERTIFICATE_ECDSA=""
ENV IP_IGNORE_LIST=""
ENV EXTERNAL_IPV4=""
ENV EXTERNAL_IPV6=""

HEALTHCHECK --start-period=15s --interval=30s --timeout=5s --retries=1 CMD /healthchecker.sh

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["/usr/bin/kurento-media-server"]
