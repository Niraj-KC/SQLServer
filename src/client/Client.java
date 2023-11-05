package client;

import util.Config;
import util.JsonHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
<p>Class for user who is going to access server.</p>
 <ol>
    <li>Chooses either to login or register</li>
    <li> <p>Provides username and password for authentication </p>
        <ol>
            <li>For login: Server returns {isAuth: true | false}</li>
            <li>
                For registration: Server returns {isAuth: true | false}
            <p>- false in case username already exists</p>
            </li>
        </ol>
    </li>
    <li>IF authorized user can start sending queries</li>
    <li>ELSE quits and program ends</li>
 </ol>
*/
public class Client {
    private Socket socket;
    private BufferedReader buffReader;
    private BufferedWriter buffWriter;
    private String username;
    private String password;
    private String type;
    private boolean isAuth = false;
    private JSONArray responseArray;

    /**
     * @param socket Socket to be connected
     * @param type In String '1' for login and '2' for register
     * @param username User's username for login or to register
     * @param password User's password for login or to set while registering
     * */
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


    /**
     * to send request to server
     * @param message Message or Query to send.
     * */
    public void sendRequest(String message){

        try {
            buffWriter.write(message);
            buffWriter.newLine();
            buffWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Manages request to send.
     * */
    public void requestManager() throws InterruptedException {

        // sending requests for authorization
        sendRequest(type);
        sendRequest(username);
        sendRequest(password);
        Thread.sleep(500);

        // if Authorized then user can continue to send request related to query
        Scanner sc = new Scanner(System.in);
        System.out.println("#A "+isAuth);
        while(socket.isConnected() && isAuth){
            System.out.print("Query: ");
            String messageToSend = sc.nextLine();
            sendRequest(messageToSend);
            Thread.sleep(500);
        }
    }

    /** Creates a new thread to continuously read incoming responses from server*/
    public void readResponse(){
        new Thread(() -> {
            String response;
            while(socket.isConnected()){
                try{
                    response = buffReader.readLine();
                    System.out.println(response);

                    // saving responses in an Array which can be further used to create log
                    responseArray.add(JsonHandler.toJson(response));

                    isAuth = (boolean)((JSONObject)responseArray.get(0)).get("isAuth");

                    if(!isAuth){
                        // if user is not authorized than closing all streams and returning to main thread

                        closeAll(socket, buffReader, buffWriter);
                        return;
                    }
                } catch (IOException e){
                    // Handling connection break
                    System.out.println("Connection broke :( ");
                    closeAll(socket, buffReader, buffWriter);
                    return;
                }
            }
        }).start();
    }


    /**
     * closes given streams if open
     * @param socket Socket to be closed
     * @param buffReader BufferedReader to be closed
     * @param buffWriter BufferedWriter to be closed
     * */
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

    public static void main(String[] args) throws IOException, InterruptedException {

        // Taking required credentials
        Scanner sc = new Scanner(System.in);
        System.out.print("Choose 1) Login 2) Register: ");
        String type = sc.nextLine().trim();
        System.out.print("Enter your username: ");
        String username = sc.nextLine().trim();
        System.out.print("Enter your password: ");
        String password = sc.nextLine().trim();

        // Connecting to server
        Socket socket = new Socket(Config.ip, Config.port);

        // Starting to interact with server
        Client client = new Client(socket, type, username, password);
        client.readResponse();
        client.requestManager();
    }

}