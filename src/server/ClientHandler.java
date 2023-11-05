package server;

import server.authorization.Authorization;
import server.queryHandler.QueryHandler;
import util.JsonHandler;

import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

/**
 * <p>For managing multiple user at a time each user is given instance of this class.</p>
 * */
public class ClientHandler implements Runnable {
    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    public Socket socket;
    private BufferedReader buffReader;
    private BufferedWriter buffWriter;
    private String username;
    private String password;
    private int type;
    private JSONObject isAuth;
    QueryHandler requestHandler;

    /**
     * @param socket Socket in which new user connected.
     * */
    public ClientHandler(Socket socket) {
        // Constructors of all the private classes
        try {
            this.socket = socket;
            this.buffWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.buffReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            this.type = Integer.parseInt(buffReader.readLine());
//            System.out.println("C: t:"+type);
            this.username = buffReader.readLine();
//            System.out.println("C: u:"+username);
            this.password = buffReader.readLine();
//            System.out.println("C: p:"+password);
            this.requestHandler = new QueryHandler(username);


        } catch (IOException e) {
            closeAll(socket, buffReader, buffWriter);
        }
    }

    /**
     * <p>It authorizes user or creates new user</p>
     * <ul>
     *     <li>
     *         If valid user than sends response <i><b>{"isAuth": true}</b></i> and continues to take further query.
     *     </li>
     *     <li>If not valid user than sends response <i><b>{"isAuth": false}</b></i> and thread ends.</li>
     * </ul>
     * */
    @Override
    public void run() {

        String requestFromClient;
        Authorization authorization = new Authorization(username, password);

        if (type == 1) {
            isAuth = authorization.login();
            if ((boolean)isAuth.get("isAuth")) {
                clientHandlers.add(this);
                sendResponse(JsonHandler.fromJson(isAuth));
                System.out.println("User "+username+"'s access was granted.");
            } else {
                sendResponse(JsonHandler.fromJson(isAuth));
                closeAll(socket, buffReader, buffWriter);
                System.out.println("User "+username+"'s access was denied.");
                return;
            }
        } else {
            isAuth = authorization.register();
            if ((boolean)isAuth.get("isAuth")) {
                clientHandlers.add(this);
                sendResponse(JsonHandler.fromJson(isAuth));
                System.out.println("New user "+username+" was created. and was granted access");
            } else {
                sendResponse(JsonHandler.fromJson(isAuth));
                closeAll(socket, buffReader, buffWriter);
                System.out.println("New user was not created.");
                return;
            }
        }

        while (socket.isConnected()) {
            try {
//                System.out.println("in while...");
                requestFromClient = buffReader.readLine();

                if(requestFromClient.trim().toLowerCase().equals("log out")){
                    closeAll(socket, buffReader, buffWriter);
                    removeClientHandler();
                    break;
                }
                JSONObject jsonObject = requestHandler.processRequest(requestFromClient);
                System.out.println("res: "+jsonObject.toJSONString());
                sendResponse(JsonHandler.fromJson(jsonObject));


            } catch (IOException e) {
                closeAll(socket, buffReader, buffWriter);
            }
        }
    }

    /**
     * To send response to user
     * */
    public void sendResponse(String responseToSend) {

        try {
            this.buffWriter.write(responseToSend);
            this.buffWriter.newLine();
            this.buffWriter.flush();

        } catch (IOException e) {
                closeAll(socket, buffReader, buffWriter);
        }
    }


    /**
     * to remove user
     * */
    public void removeClientHandler() throws IOException {
        clientHandlers.remove(this);
    }

    /**
     * closes given streams if open
     * @param socket Socket to be closed
     * @param buffReader BufferedReader to be closed
     * @param buffWriter BufferedWriter to be closed
     * */
    public void closeAll(Socket socket, BufferedReader buffReader, BufferedWriter buffWriter) {

        // handle the removeClient function
        try {
            removeClientHandler() ;
        }catch (IOException ioException)
        {
            System.out.println("Client not removed.");
        }
        try {
            if (buffReader != null) {
                buffReader.close();
            }
            if (buffWriter != null) {
                buffWriter.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.getStackTrace();
        }
    }
}
