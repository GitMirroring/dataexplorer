import socket
import sys
import json

listeningPort = 23000
listeningAddress = "0.0.0.0"
requestEOL = "\n"
replyEOL = "\n"
request = "Q"
counter = 1000
columns = 8
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
        leader = '{"dsn":1,"st":1,"tm":'
        measurements = ',"data":['
        trailer = '],"crc":0}'
        measurement_data = {"data":[]}
        data_holder = measurement_data["data"]

        try:
            print (f"Got connection from {client_address}") 

            # Receive the data in small chunks and retransmit it
            with open("output.JSON", "a") as f:
                while True:
                    data = connection.recv(16)
                    if data:
                        print(f"received {data}")
                        if data == bytes(f'{request}{requestEOL}',"ascii"):
                            row = f'{leader}{counter}{measurements}'
                            for col in range(1, columns + 1):
                                row = f'{row}{col+counter},'
                            row = row[:-1]
                            row = f'{row}{trailer}'
                            print(row, file=f)
                            row = f'{row}{replyEOL}'
                            print(f"Proper request has been received, replaying back with JSON line: {row}")
                            connection.sendall(bytes(row,'ascii'))
                            counter += 1
                            row = ""
                        else:
                            print(f"Not matching request. Terminating current connection from {client_address} ...")
                            connection.close()
                            break
                    else:
                        print(f"Current connection has been closed from remote side {client_address} ...")
                        break
        except KeyboardInterrupt:       # just to finish it nicelly when interrupted manually
            print('\nInterrupted during existing connection. Closing it ...')
            connection.close()

        except:     # in case of other errors, just close connection to not wait TIME_WAIT period to start script again
            connection.close()
            
    except KeyboardInterrupt:       # just to finish it nicelly when interrupted manually
        print('\nInterrupted while wating for connection. Well, bye ...')
        exit(1)
