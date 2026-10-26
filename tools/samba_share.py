#!/usr/bin/env python3
"""Temporary read-only APK share. Never modifies host-native file sharing."""
from pathlib import Path
import argparse
import json
import os
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local'
NAME = 'new-z8x-guide-samba'
IMAGE = 'new-z8x-guide-samba:local'
LABEL = 'local.new-z8x-guide.owner'
OWNER = 'temporary-apk-transfer-v1'


def run(*args, capture=False):
    return subprocess.run(list(args), check=True, text=True, capture_output=capture)


def inspect():
    # Distinguish an unavailable daemon from an expired/absent container.
    run('docker', 'info', '--format', '{{.ServerVersion}}', capture=True)
    found = run('docker', 'ps', '-a', '--filter', 'name=^/' + NAME + '$', '--format', '{{.ID}}', capture=True).stdout.strip()
    if not found:
        return None
    data = json.loads(run('docker', 'inspect', NAME, capture=True).stdout)[0]
    if data.get('Config', {}).get('Labels', {}).get(LABEL) != OWNER:
        raise RuntimeError('同名容器不属于本指南；不会停止或覆盖，请先核对。')
    return data


def stop():
    if inspect() is not None:
        run('docker', 'stop', NAME)
    for name in ['samba-password', 'samba-state.json']:
        (LOCAL / name).unlink(missing_ok=True)
    print('本指南共享已关闭，临时密码已删除。若曾手动暂停系统共享，请恢复原配置。')


def start():
    if inspect() is not None:
        raise RuntimeError('本指南共享已存在。需要重建时先执行 stop；不覆盖正在使用的密码。')
    packages = ROOT / 'packages'
    for path in (packages, LOCAL):
        if ',' in str(path):
            raise RuntimeError('Docker 挂载目录不能包含逗号，请移动仓库后再运行。')
    if LOCAL.is_symlink():
        raise RuntimeError('.local 不应是符号链接。')
    run(sys.executable, str(ROOT / 'tools/verify.py'))
    run('docker', 'build', '-t', IMAGE, str(ROOT / 'tools/samba'))
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    os.chmod(LOCAL, 0o700)
    password = secrets.token_hex(8)
    path = LOCAL / 'samba-password'
    path.unlink(missing_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as f:
        f.write(password + '\n')
    try:
        run('docker', 'run', '-d', '--rm', '--name', NAME,
            '--label', LABEL + '=' + OWNER,
            '--security-opt', 'no-new-privileges', '--pids-limit', '128', '--memory', '256m', '--cpus', '1',
            '-p', '445:445',
            '--mount', f'type=bind,source={packages},target=/transfer,readonly',
            '--mount', f'type=bind,source={path},target=/run/transfer-password,readonly', IMAGE)
        (LOCAL / 'samba-state.json').write_text(json.dumps({'container': NAME, 'share': 'ProjectorTransfer', 'expires_after_seconds': 3600}, indent=2) + '\n')
    except Exception:
        # A failed docker run may leave a stopped container; preserve credentials
        # if it actually started. Only remove a container after ownership checks.
        data = inspect()
        if data is not None and not data.get('State', {}).get('Running', False):
            run('docker', 'rm', NAME)
        if data is None or not data.get('State', {}).get('Running', False):
            path.unlink(missing_ok=True)
        raise
    print('\n服务器：电脑的局域网 IPv4（到电脑网络设置查看）')
    print('共享：ProjectorTransfer\n用户名：projector\n密码：' + password)
    print('仅共享 packages，只读，最多一小时。安装完成后执行 python3 tools/samba_share.py stop。')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop', 'status'])
    args = parser.parse_args()
    if not shutil.which('docker'):
        parser.exit(1, '请先安装并启动 Docker。\n')
    try:
        if args.action == 'start': start()
        elif args.action == 'stop': stop()
        else:
            data = inspect()
            print('共享不存在或已自动过期。' if data is None else '容器状态：' + data['State']['Status'])
    except (RuntimeError, OSError, subprocess.CalledProcessError) as e:
        parser.exit(1, '未完成：' + str(e) + '\n')


if __name__ == '__main__':
    main()
