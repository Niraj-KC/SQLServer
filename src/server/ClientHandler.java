package server;

import server.authorization.Authorization;
import util.JsonHandler;

import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    public Socket socket;
    private BufferedReader buffReader;
    private BufferedWriter buffWriter;
    private String username;
    private String password;
    private int type;
    private JSONObject isAuth;
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


        } catch (IOException e) {
            closeAll(socket, buffReader, buffWriter);
        }
    }

    // run method override
    @Override
    public void run() {

        String requestFromClient;
        Authorization authorization = new Authorization(username, password);

        if (type == 1) {
            isAuth = authorization.login();
            if ((boolean)isAuth.get("isAuth")) {
                clientHandlers.add(this);
                sendResponse(JsonHandler.fromJson(isAuth));
            } else {
                sendResponse(JsonHandler.fromJson(isAuth));
                closeAll(socket, buffReader, buffWriter);
                return;
            }
        } else {
            isAuth = authorization.register();
            if ((boolean)isAuth.get("isAuth")) {
                clientHandlers.add(this);
                sendResponse(JsonHandler.fromJson(isAuth));
            } else {
                sendResponse(JsonHandler.fromJson(isAuth));
                closeAll(socket, buffReader, buffWriter);
                return;
            }
        }

        while (socket.isConnected()) {
            try {
                System.out.println("in while...");
                requestFromClient = buffReader.readLine();
//                QueryHandler requestHandler = new QueryHandler(requestFromClient);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("res", "query received");
                sendResponse(JsonHandler.fromJson(jsonObject));


            } catch (IOException e) {
                closeAll(socket, buffReader, buffWriter);
            }
        }
    }

    public void sendResponse(String messageToSend) {

        try {
            this.buffWriter.write(messageToSend);
            this.buffWriter.newLine();
            this.buffWriter.flush();

        } catch (IOException e) {
                closeAll(socket, buffReader, buffWriter);
        }
    }


    public void removeClientHandler() throws IOException {
        clientHandlers.remove(this);
    }

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
//            e.getStackTrace();
        }

    }

}
