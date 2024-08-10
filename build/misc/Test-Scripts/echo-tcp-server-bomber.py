import socket
import sys
from time import time, sleep

listeningPort = 23000
listeningAddress = "0.0.0.0"
# \r (CR) -> 0D
# \n (LF) -> 0A
requestEOL = "\n" # \r\n or \n     
replyEOL = "\n"
request = "Q"
counter = 1000
columns = 8
CRC = "X"
sleepTime = 0.6 #in seconds
row = ""    # define an empty val

# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

# Bind the socket to the port
server_address = (listeningAddress, listeningPort)
print(f"Starting up on {server_address} port")
sock.bind(server_address)

# Listen for incoming connections
sock.listen(1)

while True:
    # Wait for a connection
    print(f'Waiting for a new connection...')
    try:
        connection, client_address = sock.accept()

        try:
            print (f"Got connection from {client_address}") 

            while True:
                for col in range(0, columns + 1):
                    row = f'{row}{col+counter};'
                row = f'$1;1;{row}{CRC}{replyEOL}'
                print(f"Bombing back by CSV line: {row}")
                connection.sendall(bytes(row,'ascii'))
                counter += 1
                row = ""
                sleep(sleepTime)

        except:     # in case of other errors, just close connection to not wait TIME_WAIT period to start script again
            print(f"Current connection has been closed from remote side {client_address} ...")
            connection.close()
            
    except KeyboardInterrupt:       # just to finish it nicelly when interrupted manually
        print('\nInterrupted while wating for connection. Well, bye ...')
        exit(1)


