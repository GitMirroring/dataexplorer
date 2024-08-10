# https://stackoverflow.com/questions/53305231/cant-read-pty-pseudo-terminal-file-from-external-process
# addopted by me
# https://stackoverflow.com/questions/48781155/how-to-connect-inet-socket-to-pty-device-in-python


import os, sys
from time import sleep

PTY = "/dev/ttyACM0"

master_fd, slave_fd = os.openpty()
slave = os.ttyname(slave_fd)

os.symlink(slave, PTY)
os.chmod(PTY, 0o666)
print(f'Slave {slave}, linked as {PTY}')

def safeExit():
    os.remove(PTY)
    os.close(slave_fd)
    os.close(master_fd)


try:
    for i in range(0,200): 
        d = i.to_bytes()
        os.write( master_fd, d )
        sys.stdout.write(str(i))
        sys.stdout.flush()
        sleep(1)


except KeyboardInterrupt:       # just to finish it nicelly when interrupted manually
    safeExit()
    print('\nInterrupted. Well, bye ...')
    exit(1)

safeExit()
print("\nDone")