package server;


import util.Config;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * <p><b>Main Class for our SQL Server</b></p>
 *
 * <p>It Creates Server which is listening for new users</p>
 *
 * <p>IF new user is connected it creates new thread for class ClientHandler where further process is carried out.</p>
 * */

public class Server {
    // create serverSocket class
    private ServerSocket serverSocket;

    // constructor of ServerSocket class
    public Server(ServerSocket serverSocket){
        this.serverSocket = serverSocket;
        System.out.println("Server running....");
    }

    public void serverStart(){
        try{
            // check and loop the serverSocket
            while(!serverSocket.isClosed()){
                Socket socket = serverSocket.accept();
                System.out.println("New user trying to connect...");
                // implemented an object which handle runnable class
                ClientHandler clientHandler = new ClientHandler(socket);

                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e){

        }
    }
    // this will close the server
    public void closerServer(){
        try{
            if(serverSocket != null){
                serverSocket.close();
            }
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(Config.port);
        Server server = new Server(serverSocket);
        server.serverStart();
    }
}