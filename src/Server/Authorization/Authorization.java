package Server.Authorization;

import Util.JsonHandler;
import org.json.simple.JSONObject;

public class Authorization {
    private final String path = "src/Server/Authorization/users_credentials.json";
    private final String username;
    private final String password;
    private final JSONObject jsonObject;
    private final JSONObject isAuth;
    public Authorization(String username, String password) {
        this.username = username;
        this.password = password;
        this.jsonObject = JsonHandler.readJsonFile(path);
        this.isAuth = new JSONObject();
//        System.out.println("JSON: "+jsonObject.toJSONString());
    }


    public JSONObject login(){
        String recPassword = (String)jsonObject.get(username);

        if(!jsonObject.containsKey(username)){
            isAuth.put("isAuth", false);
        }
        else {
            isAuth.put("isAuth", recPassword.equals(password));
        }

        return isAuth;
    }

    public JSONObject register() {
        if(jsonObject.containsKey(username)) {
            isAuth.put("isAuth", false);
        }
        else {
            jsonObject.put(username, password);
            JsonHandler.writeJsonFile(jsonObject, path);
            isAuth.put("isAuth", true);
        }
        return isAuth;
    }
}

class Main{
    public static void main(String[] args) {
        Authorization authorization = new Authorization("user3", "12345");
//        authorization.register();

        authorization.login();
    }
}