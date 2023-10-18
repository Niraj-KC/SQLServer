package server.queryHandler;

public class QueryHandler {
    String[] query;

    public QueryHandler(String request){
        query = request.split("\\s");
        processRequest();
    }

    public String processRequest(){
        if (query[0].equals("CREATE")){
            if (query[1].equals("DATABASE")){

            }
            else if (query[1].equals("TABLE")){

            }
        }
        else if (query[0].equals("DROP")){
            if (query[1].equals("DATABASE")){

            }
            else if (query[1].equals("TABLE")){

            }
        }
        else if (query[0].equals("UPDATE")){

        } 
        
        else if (query[0].equals("DELETE")) {
            
        } else if (query[0].equals("ALTER") && query[1].equals("TABLE")) {

            if(query[3].equals("ADD")){

            } else if (query[3].equals("DROP") && query[3].equals("COLUMN")) {
                
            }
        }
        return "";
    }


}
