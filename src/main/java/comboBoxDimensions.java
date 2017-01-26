
public class comboBoxDimensions {
	
    private String name;
    private String valid;
 
    public comboBoxDimensions(String name, String valid){
        this.name = name;
        this.valid = valid;
    }
 
    public String getEintrag(){
        return this.valid + ": " + this.name;
    }
 
    public String getName(){
    	return this.name;
    }
 
    public String isValid(){
    	return this.valid;
    }

}
