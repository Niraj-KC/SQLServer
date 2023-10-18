package util;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Has the following functions
 * <ol>
 *     <li><b>toJson: </b> String -> JSONObject</li>
 *     <li><b>fromJson: </b> JSONObject -> String</li>
 *     <li><b>readJsonFile: </b> reads .json files</li>
 *     <li><b>writeJsonFile: </b> writes .json file</li>
 * </ol>
 * */

public class JsonHandler {
    public static JSONObject toJson(String str){
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject;
        try {
            jsonObject = (JSONObject) jsonParser.parse(str);
        } catch (ParseException e) {
            System.out.println("Enter valid json string");
            return new JSONObject();
        }
        return jsonObject;
    }

    public static String fromJson(JSONObject jsonObject){
        return jsonObject.toJSONString();
    }

    public static JSONObject readJsonFile(String path){
        try {
            FileReader fileReader = new FileReader(path);
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(fileReader);
            fileReader.close();
            return jsonObject;
        } catch (IOException | ParseException e) {
            System.out.println("Enter valid file path.");
            throw new RuntimeException(e);
        }
    }

    public static void writeJsonFile(JSONObject jsonObject, String path){
        try {
            FileWriter fileWriter = new FileWriter(path);
            fileWriter.write(jsonObject.toJSONString());
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            System.out.println("Enter valid path");
        }
    }


}



// for testing
class Main{
    public static void main(String[] args) {
        JsonHandler.toJson("nnnn");
    }
}
