package server.queryHandler;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONObject;
import util.Config;
import util.customVar.ResponseKeys;
import util.customVar.Status;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryHandler {
    private String[] queryArr;
    private String query;
    private String curDbPath;
    private final String workSpacePath;


    /**
     * response = {
     *      Status: either of [created, successful, notCreated, failed, notExecuted]
     *      Details: description of Status
     *      Data: null or JSONObject's String
     * }
     * */
    JSONObject response;
    public QueryHandler(String username){

        this.response = new JSONObject();
        this.workSpacePath = Config.databaseStoragePath+"/"+username;
    }

    private void setResponse(Status status, String details, Object data){
        response.put(ResponseKeys.Status, status);
        response.put(ResponseKeys.Details, details);
        response.put(ResponseKeys.Data, data);
    }

    public JSONObject processRequest(String request){
        queryArr = request.trim().split("\\s");
        query = request.trim();
        System.out.println("Query: "+   query);

        if(!doesMatches(query, ";$")){
            setResponse(Status.failed, "Syntax-Error: semi-colon (;) missing in query", null);
            System.out.println(response.toJSONString());
        }
        else if(queryArr[0].equals("CREATE")){
            if(queryArr[1].equals("DATABASE")){
                createDatabase(query.contains("IF NOT EXISTS"));
            } else if (queryArr[1].equals("TABLE")) {
                createTable();
            }
        }
        else if (doesMatches(query, "DROP\\s+TABLE\\s+\\w+;")){
            dropTable();
        }

        else if (doesMatches(query, "ALTER\\s+TABLE\\s+\\w+\\s+DROP\\s+COLUMN\\s+\\w+;")){
            System.out.println("AT");
            dropColum();
        }

        return response;
    }

    /**
     * Creates new excel workbook with name of database
     * @implNote CREATE DATABASE database_name <br><br> CREATE DATABASE database_name IF NOT EXISTS
     * */
    private void createDatabase(boolean checkForExistence){
        String filename = queryArr[2].trim();
        if (Config.reservedKeywords.contains(filename)){
            setResponse(Status.failed, "Given database name is a reserved keyword.", null);
            return;
        }
        String newDbPath = workSpacePath+"/"+filename+".xlsx";

        if(checkForExistence && new File(newDbPath).exists()){
            curDbPath = newDbPath;
            setResponse(Status.successful, "Connected to existing database "+filename+".", null);
            return;
        }

        try {
            XSSFWorkbook wb = new XSSFWorkbook();
            wb.createSheet("TABLE");
            FileOutputStream fileOutputStream = new FileOutputStream(newDbPath);
            wb.write(fileOutputStream);
            fileOutputStream.close();
            curDbPath = newDbPath;
            setResponse(Status.created, "New database "+filename+" created.", null);
        }
        catch (IOException ioException){
            setResponse(Status.failed, "Failed to create database "+filename+". Current database remained unchanged", null);
        }

    }


    /**
     * Creates new sheet in excel workbook if sheet name is unique
     * */
    private void createTable() {
        String tableName;
        try {
            tableName = fromQueryExtract("CREATE TABLE (\\w+)\\s*\\(", query);
            System.out.println("tn: "+tableName);
        }
        catch (Exception e){
            setResponse(Status.failed, "Syntax Error", null);
            return;
        }
        try {
            if (curDbPath == null) {
                setResponse(Status.failed, "Connect to database first.", null);
                return;
            }
            if (ExcelOperations.getSheetNames(curDbPath).contains(tableName)) {
                setResponse(Status.failed, "Table name already exists.", null);
                return;
            }
            if (Config.reservedKeywords.contains(tableName)) {
                setResponse(Status.failed, "Given table name is a reserved keyword.", null);
                return;
            } else {
                XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
                if (ExcelOperations.getSheetNames(curDbPath).contains("TABLE")) {
                    wb.removeSheetAt(0);
                }
                XSSFSheet ws = wb.createSheet(tableName);

                initializeTable(wb, ws);
                
                boolean isSucc = ExcelOperations.saveExcelFile(curDbPath, wb);
                if (isSucc) {
                    setResponse(Status.created, "new table " + tableName + " created", null);
                } else {
                    setResponse(Status.failed, "Failed to create table " + tableName + " due to unknown error.", null);
                }
            }

        }
        catch (IOException ioException){
            setResponse(Status.failed, "Failed to create table " + tableName + " due to unknown error.", null);
        }

        System.out.println(response.toJSONString());
    }


    /**
     * To initialize table with given column heading in query
     * */
    private void initializeTable(XSSFWorkbook wb, XSSFSheet ws){
        String[] colHeading;
        String colStr;
        String regex = "CREATE TABLE \\w+\\((.*?)\\);";
        try {
            colStr = fromQueryExtract(regex, query);
            colHeading = colStr.split(",");

        }
        catch (Exception e){
            setResponse(Status.failed, "Syntax Error", null);
            return;
        }

        XSSFRow row0 = ws.createRow(0);
        XSSFRow row1 = ws.createRow(1);

        boolean includesIndex = colStr.contains("INT PRIMARY KEY");

        for(int i=0; i<colHeading.length+(includesIndex?0:1); i++){

            XSSFCell cell0i = row0.createCell(i);
            XSSFCell cell1i = row1.createCell(i);

            if(i==0 && !includesIndex){
                ExcelOperations.insertData(wb, cell0i, CellDataType.String, "ID");
                ExcelOperations.insertData(wb, cell1i, CellDataType.String, "Int_Primary_Key");
                continue;
            }

            String[] colNameType = colHeading[i-(includesIndex?0:1)].trim().split(" ", 2);
            System.out.println(colNameType[0]);
            System.out.println(colNameType[1]);
            System.out.println();
            ExcelOperations.insertData(wb, cell0i, CellDataType.String, colNameType[0]);
            ExcelOperations.insertData(wb, cell1i, CellDataType.String, i==0?"Int_Primary_Key":colNameType[1]);

        }

    }


    private void dropTable() {
//        System.out.println("DT");
        try{
            String tableName = fromQueryExtract(query, "DROP\\s+TABLE\\s+(\\w+)\\s*;");
            System.out.println("tn: "+tableName);
            if(curDbPath == null){
                setResponse(Status.failed, "Connect to database first.", null);
            }
            else{
                int index = ExcelOperations.getSheetNames(curDbPath).indexOf(tableName);
                if(index == -1){
                    setResponse(Status.failed, "Table "+tableName+" does not exists.", null);
                }
                else {
                XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
                wb.removeSheetAt(index);
                ExcelOperations.saveExcelFile(curDbPath, wb);
                setResponse(Status.successful, "Table "+tableName+" was removed.", null);
                }
            }
        }
        catch (Exception e){
            System.out.println("error: "+e);
            setResponse(Status.failed, "Syntax error", null);
        }
    }

    private void dropColum(){
        try {
            String tableName = fromQueryExtract(query, "ALTER\\s+TABLE\\s+(\\w+)\\s+DROP\\s+COLUMN\\s+\\w+\\s*;");
            String columnName = fromQueryExtract(query, "ALTER\\s+TABLE\\s+\\w+\\s+DROP\\s+COLUMN\\s+(\\w+)\\s*;");
            System.out.println("tn: "+ tableName+ " colN: "+columnName);

            ExcelOperations.deleteColumn(curDbPath, tableName, columnName);
            setResponse(Status.successful, "Column "+ columnName +" removed from "+tableName, null);

        }
        catch (IOException ioException){
            // for reading and writing excel file
            setResponse(Status.failed, "Failed due to unknown error", null);
        }
        catch (Exception e){
            // error in query
            System.out.println("error: "+e);
            setResponse(Status.failed, "Syntax error", null);
        }
    }



    private String fromQueryExtract(String query, String regexStr) throws Exception {
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(query);
        try {
            if (matcher.find()) {
                return matcher.group(1);
            }
            else {
                throw new Exception("No match found.");
            }
        }
        catch (IndexOutOfBoundsException indexOutOfBoundsException){
            throw new Exception("No match found.");
        }

    }
    private boolean doesMatches(String query, String regexStr){
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(query);
//        System.out.println("dm: "+matcher.matches());
        return matcher.find();
    }

}

// for testing
class Main{
    public static void main(String[] args) {
        QueryHandler queryHandler = new QueryHandler("kc");

//        System.out.println(queryHandler.processRequest("CREATE DATABASE database_name3 ").toJSONString());
        System.out.println(queryHandler.processRequest("CREATE DATABASE database_name1 IF NOT EXISTS;").toJSONString());

//        System.out.println(queryHandler.processRequest("CREATE DATABASE TABLE IF NOT EXISTS").toJSONString());
//        System.out.println(queryHandler.processRequest("CREATE DATABASE TABLE ").toJSONString());

//        System.out.println(queryHandler.processRequest("CREATE TABLE Customer2(CustomerID INT PRIMARY KEY, CustomerName String, LastName String, Gender Boolean, Age int, Phone int);"));

//        System.out.println(queryHandler.processRequest("DROP TABLE Customer;").toJSONString());
        System.out.println(queryHandler.processRequest("ALTER TABLE Customer1 DROP COLUMN bod;"));
    }
}
