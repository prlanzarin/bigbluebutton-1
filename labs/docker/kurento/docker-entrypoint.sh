#!/bin/bash -e

apt-get install -y openh264-gst-plugins-bad-1.5

rm -f /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
# Generate WebRtcEndpoint configuration
if [ "$STUN_IP" != "" ]; then
echo "stunServerAddress=$STUN_IP" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$STUN_PORT" != "" ]; then
echo "stunServerPort=$STUN_PORT" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$TURN_URL" != "" ]; then
  echo "turnURL=$TURN_URL" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$NETWORK_INTERFACES" != "" ]; then
  echo "networkInterfaces=$NETWORK_INTERFACES" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$IP_IGNORE_LIST" != "" ]; then
  echo "ipIgnoreList=$IP_IGNORE_LIST" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$EXTERNAL_IPV4" != "" ]; then
  echo "externalIPv4=$EXTERNAL_IPV4" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$EXTERNAL_IPV6" != "" ]; then
  echo "externalIPv6=$EXTERNAL_IPV6" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$NICE_AGENT_ICE_TCP" != "" ]; then
  echo "iceTcp=$NICE_AGENT_ICE_TCP" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$PEM_CERTIFICATE_RSA" != "" ]; then
  echo "pemCertificateRSA=$PEM_CERTIFICATE_RSA" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

if [ "$PEM_CERTIFICATE_ECDSA" != "" ]; then
  echo "pemCertificateECDSA=$PEM_CERTIFICATE_ECDSA" >> /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini
fi

rm -f /etc/kurento/modules/kurento/BaseRtpEndpoint.conf.ini
# Generate BaseRtpEndpoint configuration
echo "minPort=$RTP_MIN_PORT" >> /etc/kurento/modules/kurento/BaseRtpEndpoint.conf.ini
echo "maxPort=$RTP_MAX_PORT" >> /etc/kurento/modules/kurento/BaseRtpEndpoint.conf.ini

rm -f /etc/kurento/modules/kurento/RecorderEndpoint.conf.ini
# Generate RecorderEndpoint configuration
echo "gapsFix=$RECORDER_GAPS_FIX" >> /etc/kurento/modules/kurento/RecorderEndpoint.conf.ini

CONFIG=$(cat /etc/kurento/kurento.conf.json | sed '/^[ ]*\/\//d' | jq ".mediaServer.net.websocket.port = $PORT")
echo $CONFIG > /etc/kurento/kurento.conf.json

# Remove ipv6 local loop until ipv6 is supported
cat /etc/hosts | sed '/::1/d' | tee /etc/hosts > /dev/null

exec "$@"
