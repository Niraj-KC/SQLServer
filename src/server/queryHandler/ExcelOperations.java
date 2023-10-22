package server.queryHandler;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

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
                cell.setCellValue((LocalDate) data);
                cell.setCellStyle(cellStyle);
            }
            case DateTime -> {
                CreationHelper creationHelper = wb.getCreationHelper();
                CellStyle cellStyle = wb.createCellStyle();
                cellStyle.setDataFormat(creationHelper.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
                cell.setCellValue((LocalDateTime) data);
                cell.setCellStyle(cellStyle);
            }
        }

    }

}
