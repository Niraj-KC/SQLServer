package client;

import org.json.simple.JSONObject;
import util.JsonHandler;

public class ExtractData {

    protected static void getData(String resStr){
        JSONObject jsonObject = JsonHandler.toJson(resStr);
        System.out.println(jsonObject);
        JSONObject data = (JSONObject)jsonObject.get("Data");
        for (Object k: ((JSONObject)data.get("colHeadings")).keySet()){
            System.out.print(k+"    |    ");
        }
        System.out.println();

        for(Object row: data.keySet()){
            System.out.println(row);
            if(row.toString().equals("colHeadings")) continue;
            for(Object v: ((JSONObject)data.get(row)).values()){
                System.out.print(v+"    |    ");
            }
            System.out.println();
        }
    }
}
