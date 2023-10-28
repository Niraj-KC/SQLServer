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
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryHandler {
    private String query;
    private String curDbPath;
    private final String workSpacePath;


    /**
     * response = {
     * Status: either of [created, successful, notCreated, failed, notExecuted]
     * Details: description of Status
     * Data: null or JSONObject's String
     * }
     */
    JSONObject response;

    public QueryHandler(String username) {
        this.response = new JSONObject();
        this.workSpacePath = Config.databaseStoragePath + "/" + username;
    }

    private void setResponse(Status status, String details, Object data) {
        response.put(ResponseKeys.Status.toString(), status.toString());
        response.put(ResponseKeys.Details.toString(), details);
        response.put(ResponseKeys.Data.toString(), data);
    }

    public JSONObject processRequest(String request) {
        query = request.trim();
        System.out.println("Query: " + query);

        if (!doesMatches(query, ";$")) {
            setResponse(Status.failed, "Syntax-Error: semi-colon (;) missing in query", null);
            System.out.println(response.toJSONString());
        } else if (doesMatches(query, "CREATE\\s+DATABASE\\s+(\\w+)\\s*;")) {
            createDatabase(false);
        } else if (doesMatches(query, "CREATE\\s+DATABASE\\s+(\\w+)\\s+IF\\s+NOT\\s+EXISTS\\s*;")) {
            createDatabase(true);
        } else if (doesMatches(query, "CREATE\\s+TABLE\\s+(\\w+)\\s*\\(")) {
            createTable();
        } else if (doesMatches(query, "DROP\\s+TABLE\\s+\\w+;")) {
            dropTable();
        } else if (doesMatches(query, "ALTER\\s+TABLE\\s+\\w+\\s+DROP\\s+COLUMN\\s+\\w+;")) {
            dropColum();
        }

        // [^chars] -> group of character other than chars => here [^)] -> all character except ')'
        else if (doesMatches(query, "INSERT\\s+INTO\\s+\\w+\\s*\\([^)]+\\)\\s+VALUES\\s*((?:\\([^)]+\\),\\s*)*\\([^)]+\\));")) {
            insertData();
        }


        JSONObject sendRes = response;
        response = new JSONObject();
        return sendRes;
    }

    /**
     * Creates new excel workbook with name of database
     *
     * @implNote CREATE DATABASE database_name <br><br> CREATE DATABASE database_name IF NOT EXISTS
     */
    private void createDatabase(boolean checkForExistence) {
        System.out.println("cDB :" + checkForExistence);
        String filename;
        try {
            filename = fromQueryExtract(query, checkForExistence
                    ? "CREATE\\s+DATABASE\\s+(\\w+)\\s+IF\\s+NOT\\s+EXISTS\\s*;"
                    : "CREATE\\s+DATABASE\\s+(\\w+)\\s*;").get(0);
            System.out.println("fn: " + filename);
        } catch (Exception e) {
            setResponse(Status.failed, "Syntax Error", null);
            return;
        }

        if (Config.reservedKeywords.contains(filename)) {
            setResponse(Status.failed, "Given database name is a reserved keyword.", null);
            return;
        }
        String newDbPath = workSpacePath + "/" + filename + ".xlsx";

        if (checkForExistence && new File(newDbPath).exists()) {
            curDbPath = newDbPath;
            setResponse(Status.successful, "Connected to existing database " + filename + ".", null);
            return;
        }


        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("TABLE");
        boolean isSaved = ExcelOperations.saveExcelFile(newDbPath, wb);
        curDbPath = newDbPath;
        if (isSaved) setResponse(Status.created, "New database " + filename + " created.", null);
        else
            setResponse(Status.failed, "Failed to create database " + filename + ". Current database remained unchanged", null);

        System.out.println("path: " + new File(curDbPath).exists());
    }


    /**
     * Creates new sheet in excel workbook if sheet name is unique
     */
    private void createTable() {
        String tableName;
        try {
            tableName = fromQueryExtract(query, "CREATE TABLE (\\w+)\\s*\\(").get(0);
            System.out.println("tn: " + tableName);
        } catch (Exception e) {
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

        } catch (IOException ioException) {
            setResponse(Status.failed, "Failed to create table " + tableName + " due to unknown error.", null);
        }

        System.out.println(response.toJSONString());
    }


    /**
     * To initialize table with given column heading in query
     */
    private void initializeTable(XSSFWorkbook wb, XSSFSheet ws) {
        String[] colHeading;
        String colStr;
        String regex = "CREATE\\s+TABLE\\s+\\w+\\s*\\(([^)]+)\\);";
        try {
            colStr = fromQueryExtract(query, regex).get(0);
            System.out.println("#colH: " + colStr);
            colHeading = colStr.split(",\\s*");


            XSSFRow row0 = ws.createRow(0);
            XSSFRow row1 = ws.createRow(1);

            boolean includesIndex = colStr.contains("INT PRIMARY KEY");

            for (int i = 0; i < colHeading.length + (includesIndex ? 0 : 1); i++) {

                XSSFCell cell0i = row0.createCell(i);
                XSSFCell cell1i = row1.createCell(i);

                if (i == 0 && !includesIndex) {
                    ExcelOperations.insertData(wb, cell0i, CellDataType.String, "ID");
                    ExcelOperations.insertData(wb, cell1i, CellDataType.String, "Int_Primary_Key");
                    continue;
                }

                String[] colNameType = colHeading[i - (includesIndex ? 0 : 1)].trim().split(" ", 2);
                System.out.println(colNameType[0]);
                System.out.println(colNameType[1]);
                System.out.println();
                ExcelOperations.insertData(wb, cell0i, CellDataType.String, colNameType[0]);
                ExcelOperations.insertData(wb, cell1i, CellDataType.String, i == 0 ? "Int_Primary_Key" : colNameType[1]);

            }
        } catch (Exception e) {
            System.out.println("#ERROR");
            setResponse(Status.failed, "Syntax Error", null);

        }

    }


    private void dropTable() {
//        System.out.println("DT");
        try {
            String tableName = fromQueryExtract(query, "DROP\\s+TABLE\\s+(\\w+)\\s*;").get(0);
            System.out.println("tn: " + tableName);
            if (curDbPath == null) {
                setResponse(Status.failed, "Connect to database first.", null);
            } else {
                int index = ExcelOperations.getSheetNames(curDbPath).indexOf(tableName);
                if (index == -1) {
                    setResponse(Status.failed, "Table " + tableName + " does not exists.", null);
                } else {
                    XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
                    wb.removeSheetAt(index);
                    ExcelOperations.saveExcelFile(curDbPath, wb);
                    setResponse(Status.successful, "Table " + tableName + " was removed.", null);
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            setResponse(Status.failed, "Syntax error", null);
        }
    }

    private void dropColum() {
        try {
            String tableName = fromQueryExtract(query, "ALTER\\s+TABLE\\s+(\\w+)\\s+DROP\\s+COLUMN\\s+\\w+\\s*;").get(0);
            String columnName = fromQueryExtract(query, "ALTER\\s+TABLE\\s+\\w+\\s+DROP\\s+COLUMN\\s+(\\w+)\\s*;").get(0);
            System.out.println("tn: " + tableName + " colN: " + columnName);

            ExcelOperations.deleteColumn(curDbPath, tableName, columnName);
            setResponse(Status.successful, "Column " + columnName + " removed from " + tableName, null);

        } catch (IOException ioException) {
            // for reading and writing excel file
            setResponse(Status.failed, "Failed due to unknown error", null);
        } catch (Exception e) {
            // error in query
            System.out.println("error: " + e);
            setResponse(Status.failed, "Syntax error", null);
        }
    }


    private void insertData() {
        try {
            String tableName = fromQueryExtract(query, "INSERT\\s+INTO\\s+(\\w+)\\s*\\(").get(0);
            System.out.println("ok");
            String[] colHeadings = fromQueryExtract(query, "\\(([^)]+)\\)\\s*VALUES").get(0).split(",\\s*");
            ArrayList<String> rows = fromQueryExtract(fromQueryExtract(query, "VALUES\\s*((?:\\([^)]+\\),\\s*)*\\([^)]+\\));").get(0), "\\(([^)]+)\\)");
            XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
            XSSFSheet ws = wb.getSheet(tableName);


            if (ws == null) {
                setResponse(Status.failed, "Table not found.", null);
                wb.close();
                return;
            }

            int lastRow = ws.getLastRowNum() + 1;
            for (String row : rows) {
                System.out.println(row);
                String[] values = row.split(",\\s*");
                if (values.length != colHeadings.length) {
                    setResponse(Status.failed, "Columns and values does not match", null);
                    return;
                }
            }

            int[] colIndexes = new int[colHeadings.length];
            CellDataType[] cellDataTypesArr = new CellDataType[colHeadings.length];
            int idx = 0;

            for (String colHeading : colHeadings) {
                colIndexes[idx] = ExcelOperations.getColumnIndex(ws, colHeading);
                if (colIndexes[idx] == -1) {
                    setResponse(Status.failed, "No column named " + colHeading, null);
                    return;
                }
                cellDataTypesArr[idx] = ExcelOperations.getCellType(ws, colIndexes[idx]);
                idx++;
            }


            for (String row : rows) {
                System.out.println(row);
                String[] values = row.split(",\\s*");
                XSSFRow newRow = ws.createRow(lastRow);
                ExcelOperations.insertData(wb, newRow.createCell(0), CellDataType.Int_Primary_Key, lastRow - 2);
                for (int i = 0; i < values.length; i++) {
                    System.out.println("value: " + values[i]);
                    ExcelOperations.insertData(wb, newRow.createCell(colIndexes[i]), cellDataTypesArr[i],
                            ExcelOperations.typeCastStringData(values[i], cellDataTypesArr[i]));
                }
                lastRow++;
            }

            boolean isSaved = ExcelOperations.saveExcelFile(curDbPath, wb);
            if (isSaved) setResponse(Status.successful, "Data inserted", null);
            else setResponse(Status.failed, "Failed due to unknown error", null);
        } catch (Exception e) {
            e.printStackTrace();
            setResponse(Status.failed, "Syntax Error: " + e.getMessage(), null);
        }
    }

    private ArrayList<String> fromQueryExtract(String query, String regexStr) throws Exception {
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(query);
        ArrayList<String> groups = new ArrayList<String>();

        while (matcher.find()) {
            groups.add(matcher.group(1));
        }

        if (groups.isEmpty()) {
            throw new Exception("No match found.");
        }

        return groups;
    }

    private boolean doesMatches(String query, String regexStr) {
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(query);
//        System.out.println("dm: "+matcher.matches());
        return matcher.find();
    }

}

// for testing
class Main {
    public static void main(String[] args) {
        QueryHandler queryHandler = new QueryHandler("kc");

//        System.out.println(queryHandler.processRequest("CREATE DATABASE database_name3 ").toJSONString());
        System.out.println(queryHandler.processRequest("CREATE DATABASE database_name1 IF NOT EXISTS;").toJSONString());

//        System.out.println(queryHandler.processRequest("CREATE DATABASE TABLE IF NOT EXISTS").toJSONString());
//        System.out.println(queryHandler.processRequest("CREATE DATABASE TABLE ").toJSONString());

//        System.out.println(queryHandler.processRequest("CREATE TABLE Customer2(CustomerID INT PRIMARY KEY, CustomerName String, LastName String, Gender Boolean, Age int, Phone int);"));

//        System.out.println(queryHandler.processRequest("DROP TABLE Customer;").toJSONString());
//        System.out.println(queryHandler.processRequest("ALTER TABLE Customer1 DROP COLUMN bod;"));

//        System.out.println(queryHandler.processRequest("INSERT INTO Customer2 (CustomerName, Phone, Gender) VALUES (niraj, 9876054321, gg), (n, 1230456798, TRUE);").toJSONString());
        System.out.println(queryHandler.processRequest("INSERT INTO Customer2 (CustomerName,ld) VALUES (niraj,11/11/2011 1:2:24), (n, 10/10/2021 22:12:00);").toJSONString());
//        queryHandler.response = new JSONObject();
//        System.out.println(queryHandler.processRequest("INSERT INTO Customer33 (name, age) VALUES (niraj, 20), (shivani, 19);").toJSONString());
    }
}
