package org.gmod.gbol.simpleObject.generated;


import org.gmod.gbol.simpleObject.*; 


/**
 * PublicationProperty generated by hbm2java
 */
public abstract class AbstractPublicationProperty extends AbstractSimpleObject implements java.io.Serializable {


     private Integer publicationPropertyId;
     private CVTerm type;
     private Publication publication;
     private String value;
     private Integer rank;

    public AbstractPublicationProperty() {
    }

	
    public AbstractPublicationProperty(CVTerm type, Publication publication, String value) {
        this.type = type;
        this.publication = publication;
        this.value = value;
    }
    public AbstractPublicationProperty(CVTerm type, Publication publication, String value, Integer rank) {
       this.type = type;
       this.publication = publication;
       this.value = value;
       this.rank = rank;
    }
   
    public Integer getPublicationPropertyId() {
        return this.publicationPropertyId;
    }
    
    public void setPublicationPropertyId(Integer publicationPropertyId) {
        this.publicationPropertyId = publicationPropertyId;
    }
    public CVTerm getType() {
        return this.type;
    }
    
    public void setType(CVTerm type) {
        this.type = type;
    }
    public Publication getPublication() {
        return this.publication;
    }
    
    public void setPublication(Publication publication) {
        this.publication = publication;
    }
    public String getValue() {
        return this.value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    public Integer getRank() {
        return this.rank;
    }
    
    public void setRank(Integer rank) {
        this.rank = rank;
    }


   public boolean equals(Object other) {
         if ( (this == other ) ) return true;
		 if ( (other == null ) ) return false;
		 if ( !(other instanceof AbstractPublicationProperty) ) return false;
		 AbstractPublicationProperty castOther = ( AbstractPublicationProperty ) other; 
         
		 return ( (this.getType()==castOther.getType()) || ( this.getType()!=null && castOther.getType()!=null && this.getType().equals(castOther.getType()) ) )
 && ( (this.getPublication()==castOther.getPublication()) || ( this.getPublication()!=null && castOther.getPublication()!=null && this.getPublication().equals(castOther.getPublication()) ) )
 && ( (this.getRank()==castOther.getRank()) || ( this.getRank()!=null && castOther.getRank()!=null && this.getRank().equals(castOther.getRank()) ) );
   }
   
   public int hashCode() {
         int result = 17;
         
         
         result = 37 * result + ( getType() == null ? 0 : this.getType().hashCode() );
         result = 37 * result + ( getPublication() == null ? 0 : this.getPublication().hashCode() );
         
         result = 37 * result + ( getRank() == null ? 0 : this.getRank().hashCode() );
         return result;
   }   

public AbstractPublicationProperty generateClone() {
	AbstractPublicationProperty cloned = new PublicationProperty(); 
    	   cloned.type = this.type;
    	   cloned.publication = this.publication;
    	   cloned.value = this.value;
    	   cloned.rank = this.rank;
	return cloned;
}


}

