package server.queryHandler;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONObject;
import util.Config;
import util.JsonHandler;
import util.customVar.ResponseKeys;
import util.customVar.Status;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryHandler {
    private String query;
    private String curDbPath;
    private String dbName;
    private final String workSpacePath;
    private final String indexManagerPath;

    /**
     * response = {
     * Status: either of [created, successful, notCreated, failed, notExecuted]
     * Details: description of Status
     * Data: null or JSONObject
     * }
     */
    JSONObject response;
    JSONObject indexManager;

    public QueryHandler(String username) {
        this.response = new JSONObject();
        this.workSpacePath = Config.databaseStoragePath + "/" + username;
        this.indexManagerPath = workSpacePath + "/DBIndexManager.json";
    }

    /**
     * Status: either of [created, successful, notCreated, failed, notExecuted]
     * Details: description of Status
     * Data: null or JSONObject
     * */
    private void setResponse(Status status, String details, Object data) {
        response.put(ResponseKeys.Status.toString(), status.toString());
        response.put(ResponseKeys.Details.toString(), details);
        response.put(ResponseKeys.Data.toString(), data);
    }

    public JSONObject processRequest(String request) {
        this.indexManager = JsonHandler.readJsonFile(indexManagerPath);
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
        } else if (doesMatches(query, "DELETE\\s+FROM\\s+(\\w+)\\s+WHERE")) {
            System.out.println("Delete");
            deleteRow();
        } else if (doesMatches(query, "SELECT\\s+[^;]+\\s+FROM\\s+\\w+;")) {
            getSelectedData(false);
        } else if (doesMatches(query, "SELECT\\s+[^;]+\\s+FROM\\s+\\w+\\s+WHERE\\s+.*?;")) {
            getSelectedData(true);
        }
        //UPDATE Customers SET ContactName='Alfred Schmidt', City='Frankfurt' WHERE CustomerID=1;
        else if(doesMatches(query, "UPDATE\\s+\\w+\\s+SET\\s+.+\\s+WHERE\\s+.+;")){
            System.out.println("Update");
            updateValue();
        }
        else {
            setResponse(Status.failed, "Syntax Error", null);
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
            dbName = filename;
            setResponse(Status.successful, "Connected to existing database " + filename + ".", null);
            return;
        }


        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("TABLE");
        boolean isSaved = ExcelOperations.saveExcelFile(newDbPath, wb);

        if (isSaved) {
            curDbPath = newDbPath;
            dbName = filename;

            indexManager.put(dbName, new JSONObject());
            JsonHandler.writeJsonFile(indexManager, indexManagerPath);
            setResponse(Status.created, "New database " + filename + " created.", null);
        } else
            setResponse(Status.failed, "Failed to create database " + filename + ". Current database remained unchanged", null);

//        System.out.println("path: " + new File(curDbPath).exists());
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
                    ((JSONObject) indexManager.get(dbName)).put(tableName, new JSONObject());
                    ((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).put("lastPK", 0);
                    JsonHandler.writeJsonFile(indexManager, indexManagerPath);
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


    /**
     * Deletes a given table
     * */
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
                    boolean saved = ExcelOperations.saveExcelFile(curDbPath, wb);
                    if (saved) {
                        setResponse(Status.successful, "Table " + tableName + " was re" +
                                "moved.", null);
                        ((JSONObject) indexManager.get(dbName)).remove(tableName);
                        JsonHandler.writeJsonFile(indexManager, indexManagerPath);

                    } else {
                        setResponse(Status.failed, "Failed due to unknown error", null);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            setResponse(Status.failed, "Syntax error", null);
        }
    }


    /**
     * Deletes a column
     * */
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



    /**
     * Inserts given data into given column
     * */
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
            System.out.println("dbName: " + dbName);
            System.out.println("t: " + ((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).get("lastPK"));
            Integer lastPK = Integer.parseInt(((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).get("lastPK").toString());

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


//            for (String row : rows) {
//                System.out.println(row);
//                String[] values = row.split(",\\s*");
//                XSSFRow newRow = ws.createRow(lastRow);
//                ExcelOperations.insertData(wb, newRow.createCell(0), CellDataType.Int_Primary_Key, lastRow - 2);
//                for (int i = 0; i < values.length; i++) {
//                    System.out.println("value: " + values[i]);
//                    ExcelOperations.insertData(wb, newRow.createCell(colIndexes[i]), cellDataTypesArr[i],
//                            ExcelOperations.typeCastStringData(values[i], cellDataTypesArr[i]));
//                }
//
//                lastRow++;
//            }

            //inserting in in-between empty rows

            int idxForRows = 0;
            System.out.println("lastRow: " + lastRow);
            for (int cr = 2; cr < lastRow && idxForRows < rows.size(); cr++) {
                XSSFRow row = ws.getRow(cr);
                System.out.println("For-1: cr:" + cr);
                if (row == null) {
                    System.out.println("For-1-in: cr:" + cr);
                    String[] values = rows.get(idxForRows).split(",\\s*");
                    XSSFRow newRow = ws.createRow(cr);
                    ExcelOperations.insertData(wb, newRow.createCell(0), CellDataType.Int_Primary_Key, lastPK);
                    for (int i = 0; i < values.length; i++) {
                        System.out.println("value: " + values[i]);
                        ExcelOperations.insertData(wb, newRow.createCell(colIndexes[i]), cellDataTypesArr[i],
                                ExcelOperations.typeCastStringData(values[i], cellDataTypesArr[i]));
                    }
                    ((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).put("" + lastPK, cr);
                    idxForRows++;
                    lastPK++;
                }
            }

            // insert at the bottom most
            for (int cr = idxForRows; cr < rows.size(); cr++, idxForRows++, lastPK++, lastRow++) {
                System.out.println("For-2: cr:" + (cr + lastRow));

                String[] values = rows.get(idxForRows).split(",\\s*");
                XSSFRow newRow = ws.createRow(lastRow);
                ExcelOperations.insertData(wb, newRow.createCell(0), CellDataType.Int_Primary_Key, lastPK);
                for (int i = 0; i < values.length; i++) {
                    System.out.println("value: " + values[i]);
                    ExcelOperations.insertData(wb, newRow.createCell(colIndexes[i]), cellDataTypesArr[i],
                            ExcelOperations.typeCastStringData(values[i], cellDataTypesArr[i]));
                }
                ((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).put("" + lastPK, lastRow);
            }

            boolean isSaved = ExcelOperations.saveExcelFile(curDbPath, wb);
            if (isSaved) {
                setResponse(Status.successful, "Data inserted", null);
                ((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).put("lastPK", lastPK);
                JsonHandler.writeJsonFile(indexManager, indexManagerPath);
            } else setResponse(Status.failed, "Failed due to unknown error", null);
        } catch (Exception e) {
            e.printStackTrace();
            setResponse(Status.failed, "Syntax Error: " + e.getMessage(), null);
        }
    }



    /**
     * Deletes row satisfying given condition
     *
     * */
    private void deleteRow() {
        try {
            String tableName = fromQueryExtract(query, "DELETE\\s+FROM\\s+(\\w+)\\s+WHERE").get(0);
            String[] condition = fromQueryExtract(query, "WHERE (\\w+\\s*=\\s*'*[^']*'*);").get(0).split("=");
            String colName = condition[0].trim();
            String searchObj = fromQueryExtract(condition[1].trim(), "'*([^']*)'*").get(0);

            XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
            XSSFSheet ws = wb.getSheet(tableName);

            if (ws == null) {
                setResponse(Status.failed, "No table named " + tableName, null);
                return;
            }

            ArrayList<Integer> indexes = ExcelOperations.getRowIndexes(ws, colName, searchObj);

            int pk;
            for (int i : indexes) {
                pk = (int) ExcelOperations.getData(ws.getRow(i).getCell(0), CellDataType.Int_Primary_Key);
                ((JSONObject) ((JSONObject) indexManager.get(dbName)).get(tableName)).remove("" + pk);

                ws.removeRow(ws.getRow(i));
            }

            boolean saved = ExcelOperations.saveExcelFile(curDbPath, wb);

            if (!saved) {
                setResponse(Status.failed, "Failed due to unknown error.", null);
                return;
            }


            JsonHandler.writeJsonFile(indexManager, indexManagerPath);
            setResponse(Status.successful, indexes.size() + " record deleted.", null);

        } catch (Exception e) {
            e.printStackTrace();
            setResponse(Status.failed, "Syntax error", null);
        }
    }


    /**
     * returns required data from database
     * */
    private void getSelectedData(boolean withWhereClause) {
        try {
            String tableName = fromQueryExtract(query, "SELECT\\s+[^;]+\\s+FROM\\s+(\\w+).*?").get(0);
            String[] colHeadings = fromQueryExtract(query, "SELECT\\s+([^;]+)\\s+FROM").get(0).split(",\\s*");
            System.out.println("tn: " + tableName);
            JSONObject data = new JSONObject();

            XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
            XSSFSheet ws = wb.getSheet(tableName);
            XSSFRow headingRow = ws.getRow(0);

            boolean isSelectAll = colHeadings[0].equals("*");
            int totalCols = headingRow.getLastCellNum();

            String[] headingCells = new String[isSelectAll ? totalCols : colHeadings.length];
            CellDataType[] headingCellsType = new CellDataType[isSelectAll ? totalCols : colHeadings.length];
            int[] colIdx = new int[isSelectAll ? totalCols : colHeadings.length];

            data.put("colHeadings", new JSONObject());


            if (isSelectAll) {
                for (int i = 0; i < totalCols; i++) {
                    headingCells[i] = (String) ExcelOperations.getData(headingRow.getCell(i), CellDataType.String);
                    headingCellsType[i] = ExcelOperations.getCellType(ws, i);
                    ((JSONObject) data.get("colHeadings")).put(headingCells[i], headingCellsType[i].toString());
                    colIdx[i] = i;
                }
            } else {
                int i = 0;
                for (String c : colHeadings) {
                    System.out.println("col-"+i+": "+c);
                    int idx = ExcelOperations.getColumnIndex(ws, c);
                    if(idx == -1){
                        setResponse(Status.failed, "No column named "+c,  null);
                        return;
                    }
                    headingCells[i] = c;
                    headingCellsType[i] = ExcelOperations.getCellType(ws, idx);
                    ((JSONObject) data.get("colHeadings")).put(headingCells[i], headingCellsType[i].toString());
                    colIdx[i] = idx;
                    i++;
                }
            }



            if (withWhereClause) {
                System.out.println("with WHERE");

                String[] condition = fromQueryExtract(query, "SELECT\\s+[^;]+\\s+FROM\\s+\\w+\\s+WHERE\\s+(.*?);").get(0).split("=");
                String conditionCol = condition[0].trim();
                String searchObj = fromQueryExtract(condition[1].trim(), "'*([^']+)'*").get(0);


                ArrayList<Integer> rows = ExcelOperations.getRowIndexes(ws, conditionCol, searchObj);

                for (int row : rows) {
                    data.put("" + row, new JSONObject());
                    for (int idx=0; idx<colIdx.length; idx++) {
                        ((JSONObject) data.get("" + row)).put(headingCells[idx], ExcelOperations.getData(ws.getRow(row).getCell(colIdx[idx]), headingCellsType[idx]).toString());
                    }
                }

            } else {
                System.out.println("without WHERE");

                for (int r = 2; r < ws.getLastRowNum() + 1; r++) {
                    XSSFRow row = ws.getRow(r);
                    if(row==null){
                        continue;
                    }

                    data.put("" + r, new JSONObject());
                    System.out.println("-----------------------");
                    for (int idx=0; idx<colIdx.length; idx++) {
                        System.out.println("idx: "+idx);
//                        System.out.println("type: "+headingCellsType[idx]+" "+row.getCell(colIdx[idx]).getStringCellValue());
                        ((JSONObject) data.get("" + r)).put(headingCells[idx], ExcelOperations.getData(row.getCell(colIdx[idx]), headingCellsType[idx]).toString());
                    }
                }
            }

            wb.close();
            setResponse(Status.successful, "Data sent", data);
        } catch (Exception e) {
            e.printStackTrace();
            setResponse(Status.failed, "Syntax Error", null);
        }
    }



    /**
     * Update value of given column
     * */
    private void updateValue(){
        try {
            String tableName = fromQueryExtract(query, "UPDATE\\s+(\\w+)\\s+SET").get(0);
            String[] cols = fromQueryExtract(query, "SET (.*?)(?: WHERE|$)").get(0).split("\\s*,\\s*");
            String[] condition = fromQueryExtract(query, "WHERE (.*?);").get(0).split("=");

            String conditionCol  = condition[0].trim();
            String searchObj = fromQueryExtract(condition[1].trim(), "'*([^']+)'*").get(0);

            String[] toUpdateCols = new String[cols.length];
            String[] toUpdateValues = new String[cols.length];

            int i=0;
            for(String updateEle: cols){
                System.out.println("updateEle: "+updateEle);
                toUpdateCols[i] = updateEle.trim().split("=")[0].trim();
                toUpdateValues[i] = fromQueryExtract(updateEle.trim().split("=")[1].trim(), "'*([^']+)'*").get(0);
                i++;
            }


            XSSFWorkbook wb = ExcelOperations.readExcelFile(curDbPath);
            XSSFSheet ws = wb.getSheet(tableName);


            ArrayList<Integer> rowIndexes = ExcelOperations.getRowIndexes(ws, conditionCol, searchObj);

            for(int rowIdx: rowIndexes) {
                XSSFRow row = ws.getRow(rowIdx);
                for (i=0; i<toUpdateCols.length; i++) {
                    System.out.println("colIdx: "+toUpdateCols[i]);
                    int colIdx = ExcelOperations.getColumnIndex(ws, toUpdateCols[i]);
                    if (colIdx == -1) {
                        setResponse(Status.failed, "No column named" + toUpdateCols[i], null);
                        return;
                    }
                    else if(colIdx == 0){
                        setResponse(Status.failed, "Id column is auto genrated it can be updated", null);
                        return;
                    }
                    XSSFCell cellToUpdate = row.getCell(colIdx);
                    if (cellToUpdate==null) cellToUpdate = row.createCell(colIdx);
                    ExcelOperations.insertData(wb, cellToUpdate, ExcelOperations.getCellType(ws, colIdx), ExcelOperations.typeCastStringData(toUpdateValues[i], ExcelOperations.getCellType(ws, colIdx)));
                }
            }


            boolean saved = ExcelOperations.saveExcelFile(curDbPath, wb);
            if (saved){
                setResponse(Status.successful, "Values are updated successfully", null);
            }
            else {
                setResponse(Status.failed, "Failed due to unknown error", null);
            }

        }
        catch (Exception e){
            e.printStackTrace();
            setResponse(Status.failed, "Syntax Error", null);
        }
    }


    /**
     * To extract data for a string
     * @param regexStr regex for extraction
     * @param query String
     *
     * @return ArrayList
     * */
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


    /**
     * @param query string to match
     * @param regexStr regex to match string
     * @return boolean
     * <br>true - if matches
     * <br>false - if does not matches
     * */
    private boolean doesMatches(String query, String regexStr) {
        Pattern pattern = Pattern.compile(regexStr);
        Matcher matcher = pattern.matcher(query);
//        System.out.println("dm: "+matcher.matches());
        return matcher.find();
    }

}


//todo UPDATE

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
//        System.out.println(queryHandler.processRequest("INSERT INTO Customer2 (CustomerName,ld) VALUES (niraj,11/11/2011 1:2:24), (n, 10/10/2021 22:12:00);").toJSONString());
//        queryHandler.response = new JSONObject();
//        System.out.println(queryHandler.processRequest("INSERT INTO Customer33 (name, age) VALUES (niraj, 20), (shivani, 19);").toJSONString());

//        System.out.println(queryHandler.processRequest("DELETE FROM Customer2 WHERE CustomerName='n';"));


//        JSONObject jsonObject = queryHandler.processRequest("SELECT * FROM Customer2 WHERE CustomerID=14;");
//        System.out.println(jsonObject);
//        jsonObject = JsonHandler.toJson(JsonHandler.fromJson(jsonObject));
//        JSONObject data = (JSONObject)jsonObject.get("Data");
//
//        for (Object k: ((JSONObject)data.get("colHeadings")).keySet()){
//            System.out.print(k+"    |    ");
//        }
//        System.out.println();
//
//        for(Object row: data.keySet()){
//            System.out.println(row);
//            if(row.toString().equals("colHeadings")){
//                continue;
//            }
//            for(Object v: ((JSONObject)data.get(row)).values()){
//                System.out.print(v+"    |    ");
//            }
//            System.out.println();
//        }
//        System.out.println(queryHandler.processRequest("SELECT CustomerName, City FROM Customers WHERE Country = 'See's';"));
//        System.out.println(queryHandler.processRequest("SELECT CustomerName, City FROM Customers WHERE Country = true;"));
//        System.out.println(queryHandler.processRequest("SELECT CustomerName, City FROM Customers WHERE Country = 1234;"));
//        System.out.println(queryHandler.processRequest("SELECT CustomerName, City FROM Customers WHERE Country = 12:12:2013 12:12:12;"));
//        System.out.println(queryHandler.processRequest("SELECT CustomerName, City FROM Customers WHERE Country = 12:12:2013;"));
        System.out.println(queryHandler.processRequest("UPDATE Customer2 SET CustomerName='Alfred Schmidt', Gender=True , ld = 12/12/2021 1:1:1 WHERE CustomerID=4;"));

    }
}
