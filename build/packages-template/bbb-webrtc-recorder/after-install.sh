#!/bin/bash -e

BIGBLUEBUTTON_USER=bigbluebutton

case "$1" in
  configure|upgrade|1|2)

    if id $BIGBLUEBUTTON_USER > /dev/null 2>&1 ; then
      chown $BIGBLUEBUTTON_USER:$BIGBLUEBUTTON_USER /var/lib/bbb-webrtc-recorder
      chmod 0700 /var/lib/bbb-webrtc-recorder
    fi

    # Setup the LiveKit API key/secret (generate by the bbb-livekit package as
    # recorder env vars.
    if [ -f /etc/bigbluebutton/livekit.yaml ]; then
      LIVEKIT_KEY=$(yq -r '.keys | keys | .[0]' /etc/bigbluebutton/livekit.yaml)
      LIVEKIT_SECRET=$(yq -r ".keys.[\"$LIVEKIT_KEY\"]" /etc/bigbluebutton/livekit.yaml)

      if [ ! -f /etc/default/bbb-webrtc-recorder ]; then
        touch /etc/default/bbb-webrtc-recorder
      fi
      chown $BIGBLUEBUTTON_USER:$BIGBLUEBUTTON_USER /etc/default/bbb-webrtc-recorder
      chmod 0640 /etc/default/bbb-webrtc-recorder

      # Re-sync the keys so a changed keys in livekit.yaml propagate.
      sed -i '/^BBBRECORDER_LIVEKIT_APIKEY=/d; /^BBBRECORDER_LIVEKIT_APISECRET=/d' /etc/default/bbb-webrtc-recorder
      echo "BBBRECORDER_LIVEKIT_APIKEY=$LIVEKIT_KEY" >> /etc/default/bbb-webrtc-recorder
      echo "BBBRECORDER_LIVEKIT_APISECRET=$LIVEKIT_SECRET" >> /etc/default/bbb-webrtc-recorder
    fi

    systemctl enable bbb-webrtc-recorder
  ;;

  *)
    echo "## postinst called with unknown argument \`$1'" >&2
  ;;
esac

systemctl daemon-reload
