import os
import pty
import sys
from time import time, sleep

# \r (CR) -> 0D
# \n (LF) -> 0A
requestEOL = "\n" # \r\n or \n     
replyEOL = "\n"
counter = 1000
columns = 8
CRC = "0"
sleepTime = 1.0 #in seconds
row = ""    # define an empty val
PTY = "/dev/ttyACM0"

# Create a virtual serial port
master_fd, slave_fd = pty.openpty()
slave = os.ttyname(slave_fd)
os.symlink(slave, PTY)
os.chmod(PTY, 0o666)
print(f"Slave {slave} linked as {PTY}")
print(f"Master {master_fd} {type(master_fd)}")

def saveExit():
    print('saveExit cleanup')
    os.close(master_fd)
    os.close(slave_fd)
    os.remove(PTY)
    print('saveExit done')
    exit(0)

while master_fd:
    try:
        try:
            print (f"write to serial port, while waiting for some connection to read") 

            while True:
                for col in range(0, columns + 1):
                    row = f'{row}{col+counter};'
                row = f'$1;1;{row}{CRC}{replyEOL}'
                print(f"Writing CSV line: {row}")
                os.write(master_fd, bytes(row, 'ascii'))
                counter += 1
                row = ""
                sleep(sleepTime)

        except:     # in case of other errors, just close connection to not wait TIME_WAIT period to start script again
            print(f"Current virtual port will be closed ...")
            saveExit()
            
    except KeyboardInterrupt:       # just to finish it nicelly when interrupted manually
        print('\nInterrupted. Well, bye ...')
        saveExit()

saveExit()
print("\n DONE!")
