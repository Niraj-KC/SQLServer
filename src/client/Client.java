package client;

import util.Config;
import util.JsonHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {
    // private classes for the client
    private Socket socket;
    private BufferedReader buffReader;
    private BufferedWriter buffWriter;
    private String username;
    private String password;

    private String type;
    private boolean isAuth = false;
    private JSONArray responseArray;
    public Client(Socket socket, String type, String username, String password){
        try{
            // Constructors of all the private classes
            this.socket = socket;
            this.buffWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.buffReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = username;
            this.password = password;
            this.type = type;
            this.responseArray = new JSONArray();

        }catch (IOException e){
            closeAll(socket, buffReader, buffWriter);
        }
    }
    // method to send messages using thread
    public void writeMessage(String message){
        try {
            buffWriter.write(message);
            buffWriter.newLine();
            buffWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void sendRequest() throws InterruptedException {
        writeMessage(type);
        writeMessage(username);
        writeMessage(password);
        Thread.sleep(500);
        Scanner sc = new Scanner(System.in);
        System.out.println("#A "+isAuth);
        while(socket.isConnected() && isAuth){
            System.out.print("Query: ");
            String messageToSend = sc.nextLine();
            writeMessage(messageToSend);
            Thread.sleep(200);
        }
    }
    // method to read messages using thread
    public void readResponse(){
        new Thread(() -> {
            String response;

            while(socket.isConnected()){
                try{
                    response = buffReader.readLine();
                    System.out.println(response);
                    responseArray.add(JsonHandler.toJson(response));

                    isAuth = (boolean)((JSONObject)responseArray.get(0)).get("isAuth");

                    if(!isAuth){
                        closeAll(socket, buffReader, buffWriter);
                        return;
                    }
                } catch (IOException e){
                    closeAll(socket, buffReader, buffWriter);
                }
            }
        }).start();
    }
    // method to close everything in the socket
    public void closeAll(Socket socket, BufferedReader buffReader, BufferedWriter buffWriter){
        try{
            if(buffReader!= null){
                buffReader.close();
            }
            if(buffWriter != null){
                buffWriter.close();
            }
            if(socket != null){
                socket.close();
            }
        } catch (IOException e){
            e.getStackTrace();
        }
    }

    // main method
    public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
        Scanner sc = new Scanner(System.in);
        System.out.print("Choose 1) Login 2) Register: ");
        String type = sc.nextLine().trim();
        System.out.print("Enter your username: ");
        String username = sc.nextLine().trim();
        System.out.print("Enter your password: ");
        String password = sc.nextLine().trim();
        Socket socket = new Socket(Config.ip, Config.port);
        Client client = new Client(socket, type, username, password);
        client.readResponse();
        client.sendRequest();
    }

}