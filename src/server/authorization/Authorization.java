package server.authorization;

import util.Config;
import util.JsonHandler;
import org.json.simple.JSONObject;


/**
 * To authorize user
 * @implNote Set variable path to the file in which all user credentials are stored.
 * */
public class Authorization {
    private final String path = Config.credPath;
    private final String username;
    private final String password;
    private final JSONObject jsonObject;
    private final JSONObject isAuth;

    /**
     * @param username username to be authorizing
     * @param password username to be authorizing
     * */
    public Authorization(String username, String password) {
        this.username = username;
        this.password = password;
        this.jsonObject = JsonHandler.readJsonFile(path);
        this.isAuth = new JSONObject();
//        System.out.println("JSON: "+jsonObject.toJSONString());
    }


    /**
     * for authorizing existing user
     * @return JSONObject {"isAuth": true} if valid user.
     * */
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

    /**
     * For creating new user
     * @return JSONObject {"isAuth": true} if valid user.
     * */
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


//for testing purpose
class Main{
    public static void main(String[] args) {
        Authorization authorization = new Authorization("user3", "12345");
//        authorization.register();

        authorization.login();
    }
}