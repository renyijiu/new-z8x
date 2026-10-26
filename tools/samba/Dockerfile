FROM alpine:3.22
RUN apk add --no-cache samba-server samba-common-tools \
    && adduser -D -H -s /sbin/nologin projector
COPY smb.conf /etc/samba/smb.conf
COPY start.sh /start.sh
RUN chmod 755 /start.sh
ENTRYPOINT ["/start.sh"]
