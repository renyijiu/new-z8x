#!/usr/bin/env python3
"""Read-only TCP connection checks; timeout/unreachable never means closed."""
import argparse
import errno
import ipaddress
import socket


def check(host, port):
    try:
        with socket.create_connection((host, port), timeout=3):
            return 'OPEN', '可连接'
    except OSError as e:
        if e.errno == errno.ECONNREFUSED:
            return 'REFUSED', '连接被拒绝'
        return 'UNKNOWN', str(e)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('ip', help='投影仪当前局域网 IPv4')
    args = parser.parse_args()
    try:
        address = ipaddress.IPv4Address(args.ip)
        if not address.is_private or address.is_loopback or address.is_unspecified or address.is_multicast or address.is_link_local:
            raise ValueError('请填写投影仪的局域网 IPv4')
    except ValueError as e:
        parser.error(str(e))
    results = []
    for port in (22, 5555):
        state, detail = check(str(address), port)
        results.append(state)
        print(f'{address}:{port} {state} — {detail}')
    return 0 if all(x == 'REFUSED' for x in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
