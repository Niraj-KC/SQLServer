package server.queryHandler;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class ExcelOperations {

    public static XSSFWorkbook readExcelFile(String path) throws IOException {

        FileInputStream fileInputStream = new FileInputStream(path);
        XSSFWorkbook wb = new XSSFWorkbook(fileInputStream);
        fileInputStream.close();

        return wb;
    }

    public static boolean saveExcelFile(String path, XSSFWorkbook wb) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(path);
            wb.write(fileOutputStream);
            fileOutputStream.close();
            return true;
        }
        catch (IOException ioException){
            return false;
        }
    };

    public static ArrayList<String> getSheetNames (String path) throws IOException {
        ArrayList<String> sheetNames = new ArrayList<>();
        XSSFWorkbook wb = readExcelFile(path);
        for(int i=0; i<wb.getNumberOfSheets(); i++){
            sheetNames.add(wb.getSheetName(i));
        }
        wb.close();
        return sheetNames;
    }

    public static void insertData(XSSFWorkbook wb, XSSFCell cell, CellDataType type, Object data){

        switch (type){
            case Int -> cell.setCellValue((Integer) data);
            case Double -> cell.setCellValue((double) data);
            case String -> cell.setCellValue((String) data);
            case Boolean -> cell.setCellValue((boolean) data);
            case Date -> {
                CreationHelper creationHelper = wb.getCreationHelper();
                CellStyle cellStyle = wb.createCellStyle();
                cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat("dd/mm/yyyy"));
                cell.setCellValue((Date) data);
                cell.setCellStyle(cellStyle);
            }
            case DateTime -> {
                CreationHelper creationHelper = wb.getCreationHelper();
                CellStyle cellStyle = wb.createCellStyle();
                cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
                cell.setCellValue((Date) data);
                cell.setCellStyle(cellStyle);
            }
        }

    }

    public static Object getData(XSSFCell cell , CellDataType type){
        Object data;
        switch (type){
            case String -> data = cell.getStringCellValue();
            case Int, Int_Primary_Key -> data = Double.valueOf(cell.getNumericCellValue()).intValue();
            case Double -> data = cell.getNumericCellValue();
            case Date, DateTime-> data = cell.getDateCellValue();
            case Boolean -> data = cell.getBooleanCellValue();
            default -> data = null;
        }
        return data;
    }


    public static CellDataType getCellType(XSSFSheet ws, XSSFCell cell){
        return CellDataType.valueOf(ws.getRow(1).getCell(cell.getColumnIndex()).getStringCellValue());
    }

    public static void deleteColumn(String path, String wsName, String colName) throws IOException {
        XSSFWorkbook wb = readExcelFile(path);
        XSSFSheet ws = wb.getSheet(wsName);

        XSSFRow colHeadingsRow = ws.getRow(0);
        XSSFRow colTypeRow = ws.getRow(1);

        ArrayList<String> colHeading = new ArrayList<>();
        ArrayList<CellDataType> colDataTypes = new ArrayList<>();

//        System.out.println("cols: "+colHeadingsRow.getLastCellNum());
        for(int i=0; i<colHeadingsRow.getLastCellNum(); i++){
            if(i==0){
                colHeading.add(colHeadingsRow.getCell(i).getStringCellValue());
                colDataTypes.add(CellDataType.Int_Primary_Key);
                continue;
            }
            System.out.println(colHeadingsRow.getCell(i).getStringCellValue());
            colHeading.add(colHeadingsRow.getCell(i).getStringCellValue());
            colDataTypes.add(CellDataType.valueOf(colTypeRow.getCell(i).getStringCellValue()));
        }

        int indexOfColToRemove = colHeading.indexOf(colName);
//        System.out.println("\n"+"indexOfColToRemove: "+indexOfColToRemove);

        if(indexOfColToRemove == colTypeRow.getLastCellNum()){
//            System.out.println("if...");
//            System.out.println("Rows: "+ws.getLastRowNum());
            for(int i=0; i<=ws.getLastRowNum(); i++){
                if(ws.getRow(i)==null) continue;
                ws.getRow(i).removeCell(ws.getRow(i).getCell(indexOfColToRemove));
            }

        }
        else {
//            System.out.println("else....");
//            System.out.println("Rows: "+ws.getLastRowNum());
            int lastColIndex = colHeadingsRow.getLastCellNum()-1;
            CellDataType lastCellType = colDataTypes.get(lastColIndex);

            for(int i=0; i<=ws.getLastRowNum(); i++){
                XSSFRow curRow = ws.getRow(i);
                XSSFCell cellToRemove = curRow.getCell(indexOfColToRemove);
                XSSFCell lastCell = curRow.getCell(lastColIndex);

                if(cellToRemove == null){
                    cellToRemove = curRow.createCell(indexOfColToRemove);
                }
                if(lastCell==null){
//                    System.out.println("continue..");
                    curRow.removeCell(cellToRemove);
                    continue;
                }
                if(i==0 || i==1){
                    insertData(wb, cellToRemove, CellDataType.String, getData(lastCell, CellDataType.String));
                }
                else {
//                    System.out.println("data: "+getData(lastCell, lastCellType));
                    insertData(wb, cellToRemove, lastCellType, getData(lastCell, lastCellType));
                }
                curRow.removeCell(lastCell);
            }
        }

        saveExcelFile(path, wb);
    }

}
