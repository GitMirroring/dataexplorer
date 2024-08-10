import os
import pty
import sys
import json
from time import time, sleep

# \r (CR) -> 0D
# \n (LF) -> 0A
requestEOL = "\n" # \r\n or \n     
replyEOL = "\n"
counter = 1000
columns = 8
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
        leader = '{"dsn":1,"st":1,"tm":'
        measurements = ',"data":['
        trailer = '],"crc":0}'
        measurement_data = {"data":[]}
        data_holder = measurement_data["data"]
        try:
            print (f"write to serial port, while waiting for some connection to read") 

            while True:
                row = f'{leader}{counter}{measurements}'
                for col in range(1, columns + 1):
                    row = f'{row}{col+counter},'
                row = row[:-1]
                row = f'{row}{trailer}'
                row = f'{row}{replyEOL}'
                print(f"write JSON line: {row}")                            
                os.write(master_fd, bytes(row, 'ascii'))
                counter += 1
                row = ""
                sleep(sleepTime)

        except: # in case of other errors, just close connection to not wait TIME_WAIT period to start script again
            print(f"Current virtual port will be closed ...")
            saveExit()
            
    except KeyboardInterrupt: # just to finish it nicelly when interrupted manually
        print('\nInterrupted. Well, bye ...')
        saveExit()

saveExit()
print("\n DONE!")
