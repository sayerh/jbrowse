package org.bbop.apollo

import grails.transaction.Transactional
import grails.compiler.GrailsCompileStatic
import org.apache.shiro.SecurityUtils
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.TranslationTable
import org.bbop.apollo.web.util.JSONUtil
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONException
import org.codehaus.groovy.grails.web.json.JSONObject
import org.gmod.gbol.bioObject.AbstractBioFeature
import org.gmod.gbol.bioObject.util.BioObjectUtil

//import org.json.JSONObject

/**
 * taken from AbstractBioFeature
 */
@GrailsCompileStatic
@Transactional
class FeatureService {

    public static final String MANUALLY_SET_TRANSLATION_START = "Manually set translation start";
    public static final String MANUALLY_SET_TRANSLATION_END = "Manually set translation end";

    NameService nameService
    ConfigWrapperService configWrapperService
    TranscriptService transcriptService
    CvTermService cvTermService
    ExonService exonService
    CdsService cdsService
    NonCanonicalSplitSiteService nonCanonicalSplitSiteService
    FeatureRelationshipService featureRelationshipService

    def addProperty(Feature feature, FeatureProperty property) {
        int rank = 0;
        for (FeatureProperty fp : feature.getFeatureProperties()) {
            if (fp.getType().equals(property.getType())) {
                if (fp.getRank() > rank) {
                    rank = fp.getRank();
                }
            }
        }
        property.setRank(rank + 1);
        boolean ok = feature.getFeatureProperties().add(property);

    }

    public boolean deleteProperty(Feature feature, FeatureProperty property) {
        for (FeatureProperty fp : feature.getFeatureProperties()) {
            if (fp.getType().equals(property.getType()) && fp.getValue().equals(property.getValue())) {
                feature.getFeatureProperties().remove(fp);
                return true;
            }
        }
    }

    /**
     * Is there a feature property called "owner"
     * @param feature
     * @return
     */
    public User getOwner(Feature feature) {
//        Collection<CVTerm> ownerCvterms = conf.getCVTermsForClass("Owner");
        List<CVTerm> ownerCvTerms = CVTerm.findAllByName("Owner")

        for (FeatureProperty fp : feature.getFeatureProperties()) {
            if (fp.type in ownerCvTerms) {
                return User.findByUsername(fp.type.name)
            }
//            if (ownerCvterms.contains(fp.getType())) {
//                return new User(fp, conf);
//            }
        }

        // if no owner found, try to get the first owner found in an ancestor
        for (FeatureRelationship fr : feature.getParentFeatureRelationships()) {
//            Feature parent = (AbstractBioFeature)BioObjectUtil.createBioObject(fr.getObjectFeature(), getConfiguration());
            Feature parent = fr.objectFeature // may be subject Feature . . not sure
            User parentOwner = getOwner(parent)
            if (parentOwner != null) {
                return parentOwner;
            }
        }

        return null;
    }

    public void setUser(Feature feature, User owner) {
//        Collection<CVTerm> ownerCvterms = conf.getCVTermsForClass("User");
        List<CVTerm> ownerCvTerms = CVTerm.findAllByName("Owner")

        for (FeatureProperty fp : feature.getFeatureProperties()) {
//            if (ownerCvterms.contains(fp.getType())) {
            if (fp.type in ownerCvTerms) {
                feature.getFeatureProperties().remove(fp);
                break;
            }
        }
        addProperty(feature,owner);
    }

    /** Set the owner of this feature.
     *
     * @param owner - User of this feature
     */
    public void setOwner(Feature feature,String owner) {
        User user = User.findByUsername(owner)
        if(user){
            setOwner(feature,user)
        }
        else{
            throw new AnnotationException("User ${owner} not found")
        }
//        setOwner(new User(owner));
    }

    public void setOwner(Feature feature,User user){
        addProperty(feature,user)
    }

    public static FeatureLocation convertJSONToFeatureLocation(JSONObject jsonLocation, Feature sourceFeature) throws JSONException {
        FeatureLocation gsolLocation = new FeatureLocation();
        gsolLocation.setFmin(jsonLocation.getInt(FeatureStringEnum.FMIN.value));
        gsolLocation.setFmax(jsonLocation.getInt(FeatureStringEnum.FMAX.value));
        gsolLocation.setStrand(jsonLocation.getInt(FeatureStringEnum.STRAND.value));
        gsolLocation.setSourceFeature(sourceFeature);
        return gsolLocation;
    }

    /** Get features that overlap a given location.  Compares strand as well as coordinates.
     *
     * @param location - FeatureLocation that the features overlap
     * @return Collection of AbstractSingleLocationBioFeature objects that overlap the FeatureLocation
     */
    public Collection<Feature> getOverlappingFeatures(FeatureLocation location) {
        return getOverlappingFeatures(location, true);
    }

    /** Get features that overlap a given location.
     *
     * @param location - FeatureLocation that the features overlap
     * @param compareStrands - Whether to compare strands in overlap
     * @return Collection of AbstractSingleLocationBioFeature objects that overlap the FeatureLocation
     */
    public Collection<Feature> getOverlappingFeatures(FeatureLocation location, boolean compareStrands) {
        LinkedList<Feature> overlappingFeatures =
                new LinkedList<Feature>();
        int low = 0;
        int high = Feature.count - 1;
        int index = -1;
        while (low <= high) {
            int mid = (low + ((high - low) / 2)).intValue();
            Feature.all.get(mid)
            Feature feature = Feature.all.get(mid)
            if (feature == null) {
//                uniqueNameToStoredUniqueName.remove(features.get(mid).getUniqueName());
//                features.remove(mid);
                return getOverlappingFeatures(location, compareStrands);
            }
            if (overlaps(feature.featureLocation,location, compareStrands)) {
                index = mid;
                break;
            }
            else if (feature.getFeatureLocation().getFmin() < location.getFmin()) {
                low = mid + 1;
            }
            else {
                high = mid - 1;
            }
        }
        if (index >= -1) {
            for (int i = index; i >= 0; --i) {
                Feature feature = Feature.all.get(i)
                if (feature == null) {
//                    uniqueNameToStoredUniqueName.remove(features.get(i).getUniqueName());
//                    features.remove(i);
                    return getOverlappingFeatures(location, compareStrands);
                }
                if (overlaps(feature.featureLocation,location, compareStrands)) {
                    overlappingFeatures.addFirst(feature);
                }
                else {
                    break;
                }
            }
            for (int i = index + 1; i < Feature.count ; ++i) {
                Feature feature = Feature.all.get(i)
                if (overlaps(feature.featureLocation,location, compareStrands)) {
                    overlappingFeatures.addLast(feature);
                }
                else {
                    break;
                }
            }
        }
        return overlappingFeatures;
    }

    void updateNewGsolFeatureAttributes(Feature gsolFeature, Feature sourceFeature) {
        gsolFeature.setIsAnalysis(false);
        gsolFeature.setIsObsolete(false);
        gsolFeature.setDateCreated(new Date()); //new Timestamp(new Date().getTime()));
        gsolFeature.setLastUpdated(new Date()); //new Timestamp(new Date().getTime()));
        if (sourceFeature != null) {
            gsolFeature.getFeatureLocations().iterator().next().setSourceFeature(sourceFeature);
        }
        for (FeatureRelationship fr : gsolFeature.getChildFeatureRelationships()) {
            updateNewGsolFeatureAttributes(fr.getSubjectFeature(), sourceFeature);
        }
    }

    def generateTranscript(JSONObject jsonTranscript, String trackName,boolean isPseudogene = false ) {
        Gene gene = jsonTranscript.has(FeatureStringEnum.PARENT_ID.value) ? (Gene) Feature.findByUniqueName(jsonTranscript.getString(FeatureStringEnum.PARENT_ID.value)) : null;
        Transcript transcript = null
        boolean useCDS = configWrapperService.useCDS()
        FeatureLazyResidues featureLazyResidues = FeatureLazyResidues.findByName(trackName)
        if (gene != null) {
//            Feature gsolTranscript = convertJSONToFeature(jsonTranscript, featureLazyResidues);
            transcript = (Transcript) convertJSONToFeature(jsonTranscript, featureLazyResidues);
//            transcript = (Transcript) BioObjectUtil.createBioObject(gsolTranscript, bioObjectConfiguration);
            if (transcript.getFmin() < 0 || transcript.getFmax() < 0) {
                throw new AnnotationException("Feature cannot have negative coordinates")
//                throw new AnnotationEditorServiceException("Feature cannot have negative coordinates");
            }

//            setOwner(transcript, (String) session.getAttribute("username"));
            setOwner(transcript, (String) SecurityUtils?.subject?.principal);



            if (!useCDS || transcriptService.getCDS(transcript) == null) {
                calculateCDS(transcript);
            }

            addTranscriptToGene(gene, transcript);
            nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript);
            transcript.name = nameService.generateUniqueName(transcript)
//            transcriptService.updateTranscriptAttributes(transcript);
        } else {
            FeatureLocation featureLocation = convertJSONToFeatureLocation(jsonTranscript.getJSONObject(FeatureStringEnum.LOCATION.value),featureLazyResidues)
            Collection<Feature> overlappingFeatures = getOverlappingFeatures(featureLocation);
            for (Feature feature : overlappingFeatures) {
                if (feature instanceof Gene && !(feature instanceof Pseudogene) && configWrapperService.overlapper != null) {
                    Gene tmpGene = (Gene) feature;
                    Transcript gsolTranscript = (Transcript) convertJSONToFeature(jsonTranscript,  featureLazyResidues);
                    updateNewGsolFeatureAttributes(gsolTranscript, featureLazyResidues);
//                    Transcript tmpTranscript = (Transcript) BioObjectUtil.createBioObject(gsolTranscript, bioObjectConfiguration);
                    if (gsolTranscript.getFmin() < 0 || gsolTranscript.getFmax() < 0) {
                        throw new AnnotationException("Feature cannot have negative coordinates");
                    }
//                    setOwner(tmpTranscript, (String) session.getAttribute("username"));
//                    String username = SecurityUtils?.subject?.principal
                    setOwner(transcript, (String) SecurityUtils?.subject?.principal);
                    if (!useCDS || transcriptService.getCDS(gsolTranscript) == null) {
                        calculateCDS(gsolTranscript);
                    }
                    gsolTranscript.name = nameService.generateUniqueName(gsolTranscript)
//                    updateTranscriptAttributes(tmpTranscript);
                    if (overlaps(gsolTranscript, tmpGene)) {
                        transcript = gsolTranscript;
                        gene = tmpGene;
//                        editor.addTranscript(gene, transcript);
                        addTranscriptToGene(gene,transcript)
                        nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript);
                        break;
                    } else {
//                        editor.getSession().endTransactionForFeature(feature);
                    }
                } else {
//                    editor.getSession().endTransactionForFeature(feature);
                }
            }
        }
        if (gene == null) {
            JSONObject jsonGene = new JSONObject();
            jsonGene.put(FeatureStringEnum.CHILDREN.value, new JSONArray().put(jsonTranscript));
            jsonGene.put(FeatureStringEnum.LOCATION.value, jsonTranscript.getJSONObject(FeatureStringEnum.LOCATION.value));
            CVTerm cvTerm = CVTerm.findByName(isPseudogene ? FeatureStringEnum.PSEUDOGENE.value :FeatureStringEnum.GENE.value )
            jsonGene.put(FeatureStringEnum.TYPE.value, cvTermService.convertCVTermToJSON(cvTerm));

            Feature gsolGene = convertJSONToFeature(jsonGene, featureLazyResidues);
            updateNewGsolFeatureAttributes(gsolGene, featureLazyResidues);
//            gene = (Gene) BioObjectUtil.createBioObject(gsolGene, bioObjectConfiguration);
            if (gsolGene.getFmin() < 0 || gsolGene.getFmax() < 0) {
                throw new AnnotationException("Feature cannot have negative coordinates");
            }
            setOwner(gene, (String) SecurityUtils?.subject?.principal);
            transcript = transcriptService.getTranscripts(gene).iterator().next();
            if (!useCDS || transcriptService.getCDS(transcript) == null) {
                calculateCDS(transcript);
            }
            // I don't thikn that this does anything
            addFeature(gene);
            transcript.name = nameService.generateUniqueName(transcript)
            nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript);
            gsolGene.save(insert:true)
            transcript.save(flush:true)
        }
        return transcript;

    }

    Feature getTopLevelFeature(Feature feature) {
        Collection<? extends Feature> parents = feature.getParentFeatureRelationships()*.objectFeature
        if (parents.size() > 0) {
            return getTopLevelFeature(parents.iterator().next());
        } else {
            return feature;
        }
    }


    def addFeature(Feature feature) {

//        Feature topLevelFeature = getTopLevelFeature(feature);

        if (feature instanceof Gene) {
            for (Transcript transcript : transcriptService.getTranscripts((Gene) feature)) {
                removeExonOverlapsAndAdjacencies(transcript);
            }
        } else if (feature instanceof Transcript) {
            removeExonOverlapsAndAdjacencies((Transcript) feature);
        }

        // event fire
//        fireAnnotationChangeEvent(feature, topLevelFeature, AnnotationChangeEvent.Operation.ADD);

//        getSession().addFeature(feature);

        // old version was deleting the feature .. . badness!! , we want to update if its there.

//        if (uniqueNameToStoredUniqueName.containsKey(feature.getUniqueName())) {
//            deleteFeature(feature);
//        }
//        AbstractSingleLocationBioFeature topLevelFeature = getTopLevelFeature(feature);
//        features.add(new FeatureData(topLevelFeature));
//        if (getFeatureByUniqueName(topLevelFeature.getUniqueName()) == null) {
//            beginTransactionForFeature(topLevelFeature);
//        }
//        indexFeature(topLevelFeature);
//        Collections.sort(features, new FeatureDataPositionComparator());

    }

    def addTranscriptToGene(Gene gene, Transcript transcript) {
        removeExonOverlapsAndAdjacencies(transcript);
//        gene.addTranscript(transcript);
        CVTerm partOfCvterm = cvTermService.partOf

        // no feature location, set location to transcript's
        if (gene.getSingleFeatureLocation() == null) {
            FeatureLocation transcriptFeatureLocation = transcript.getSingleFeatureLocation()
            FeatureLocation featureLocation = new FeatureLocation()
            featureLocation.properties = transcriptFeatureLocation.properties
            featureLocation.id = null
            featureLocation.save()
            gene.setFeatureLocation( featureLocation );
        }
        else {
            // if the transcript's bounds are beyond the gene's bounds, need to adjust the gene's bounds
            if (transcript.getSingleFeatureLocation().getFmin() < gene.getSingleFeatureLocation().getFmin()) {
                gene.getSingleFeatureLocation().setFmin(transcript.getSingleFeatureLocation().getFmin());
            }
            if (transcript.getSingleFeatureLocation().getFmax() > gene.getSingleFeatureLocation().getFmax()) {
                gene.getSingleFeatureLocation().setFmax(transcript.getSingleFeatureLocation().getFmax());
            }
        }

        // add transcript
        int rank = 0;
        //TODO: do we need to figure out the rank?
        FeatureRelationship featureRelationship = new FeatureRelationship(
                type: partOfCvterm.iterator().next()
                ,objectFeature: gene
                ,subjectFeature: transcript
                ,rank: rank
        ).save()
        gene.childFeatureRelationships.add(featureRelationship)


        updateGeneBoundaries(gene);

//        getSession().indexFeature(transcript);

        // event fire
//        TODO: determine event model?
//        fireAnnotationChangeEvent(transcript, gene, AnnotationChangeEvent.Operation.ADD);
    }


    def removeExonOverlapsAndAdjacencies(Transcript transcript) {
        Collection<Exon> exons = transcriptService.getExons(transcript)
        if (transcriptService.getExons(transcript).size() <= 1) {
            return;
        }
        List<Exon> sortedExons= new LinkedList<Exon>(exons);
        Collections.sort(sortedExons,new FeaturePositionComparator<Exon>(false))
        int inc = 1;
        for (int i = 0; i < sortedExons.size() - 1; i += inc) {
            inc = 1;
            Exon leftExon = sortedExons.get(i);
            for (int j = i + 1; j < sortedExons.size(); ++j) {
                Exon rightExon = sortedExons.get(j);
                overlaps(leftExon,rightExon)
                if (overlaps(leftExon,rightExon) || isAdjacentTo(leftExon.getFeatureLocation(),rightExon.getFeatureLocation())) {
                    try {
                        exonService.mergeExons(leftExon, rightExon);
                    } catch (AnnotationException e) {
                        log.error(e)
                    }
                    ++inc;
                }
            }
        }
    }

    /** Checks whether this AbstractSimpleLocationBioFeature is adjacent to the FeatureLocation.
     *
     * @param location - FeatureLocation to check adjacency against
     * @return true if there is adjacency
     */
    public boolean isAdjacentTo(FeatureLocation leftLocation,FeatureLocation location) {
        return isAdjacentTo(leftLocation,location, true);
    }

    public boolean isAdjacentTo(FeatureLocation leftFeatureLocation,FeatureLocation rightFeatureLocation, boolean compareStrands) {
        if (leftFeatureLocation.getSourceFeature() != rightFeatureLocation.getSourceFeature() &&
                !leftFeatureLocation.getSourceFeature().equals(rightFeatureLocation.getSourceFeature())) {
            return false;
        }
        int thisFmin = leftFeatureLocation.getFmin();
        int thisFmax = leftFeatureLocation.getFmax();
        int thisStrand = leftFeatureLocation.getStrand();
        int otherFmin = rightFeatureLocation.getFmin();
        int otherFmax = rightFeatureLocation.getFmax();
        int otherStrand = rightFeatureLocation.getStrand();
        boolean strandsOverlap = compareStrands ? thisStrand == otherStrand : true;
        if (strandsOverlap &&
                (thisFmax == otherFmin ||
                        thisFmin == otherFmax)) {
            return true;
        }
        return false;
    }

    def overlaps(Feature leftFeature, Feature rightFeature,boolean compareStrands = true) {
        return overlaps(leftFeature.featureLocation,rightFeature.featureLocation,compareStrands)
    }

    def overlaps(FeatureLocation leftFeatureLocation, FeatureLocation rightFeatureLocation,boolean compareStrands = true) {
        if (leftFeatureLocation.getSourceFeature() != rightFeatureLocation.getSourceFeature() &&
                !leftFeatureLocation.getSourceFeature().equals(rightFeatureLocation.getSourceFeature())) {
            return false;
        }
        int thisFmin = leftFeatureLocation.getFmin();
        int thisFmax = leftFeatureLocation.getFmax();
        int thisStrand = leftFeatureLocation.getStrand();
        int otherFmin = rightFeatureLocation.getFmin();
        int otherFmax = rightFeatureLocation.getFmax();
        int otherStrand = rightFeatureLocation.getStrand();
        boolean strandsOverlap = compareStrands ? thisStrand == otherStrand : true;
        if (strandsOverlap &&
                (thisFmin <= otherFmin && thisFmax > otherFmin ||
                        thisFmin >= otherFmin && thisFmin < otherFmax)) {
            return true;
        }
        return false;
    }


    def calculateCDS(Transcript transcript) {
        // NOTE: isPseudogene call seemed redundant with isProtenCoding
        calculateCDS(transcript,false)
//        if (transcriptService.isProteinCoding(transcript) && (transcriptService.getGene(transcript) == null)) {
////            calculateCDS(editor, transcript, transcript.getCDS() != null ? transcript.getCDS().getStopCodonReadThrough() != null : false);
////            calculateCDS(transcript, transcript.getCDS() != null ? transcript.getCDS().getStopCodonReadThrough() != null : false);
//            calculateCDS(transcript, transcriptService.getCDS(transcript) != null ? transcriptService.getStopCodonReadThrough(transcript) != null : false);
//        }
    }

    def calculateCDS(Transcript transcript,boolean readThroughStopCodon) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            setLongestORF(transcript, readThroughStopCodon);
            return;
        }
        boolean manuallySetStart = isManuallySetTranslationStart(cds);
        boolean manuallySetEnd = isManuallySetTranslationEnd(cds);
        if (manuallySetStart && manuallySetEnd) {
            return;
        }
        if (!manuallySetStart && !manuallySetEnd) {
            setLongestORF(transcript, readThroughStopCodon);
        } else if (manuallySetStart) {
            setTranslationStart(transcript, cds.getFeatureLocation().getStrand().equals(-1) ? cds.getFmax() - 1 : cds.getFmin(), true, readThroughStopCodon);
        } else {
            setTranslationEnd(transcript, cds.getFeatureLocation().getStrand().equals(-1) ? cds.getFmin() : cds.getFmax() - 1, true);
        }
    }

    public boolean isManuallySetTranslationStart(CDS cds) {
        for (Comment comment : getComments(cds)) {
            if (comment.value.equals(MANUALLY_SET_TRANSLATION_START)) {
                return true;
            }
        }
        return false;
    }

    /** Get comments for this feature.
     *
     * @return Comments for this feature
     */
    public Collection<Comment> getComments(Feature feature) {
        CVTerm commentCvTerm = cvTermService.getTerm(FeatureStringEnum.COMMENT)
//        Collection<CVTerm> commentCvterms = conf.getCVTermsForClass("Comment");
        List<Comment> comments = new ArrayList<Comment>();

        for (FeatureProperty fp : feature.getFeatureProperties()) {
            if (commentCvTerm==fp.getType()) {
                comments.add((Comment) fp);
            }
        }
        Collections.sort(comments, new Comparator<Comment>() {

//            @Override
            public int compare(Comment comment1, Comment comment2) {
                if (comment1.getType().equals(comment2.getType())) {
                    return new Integer(comment1.getRank()).compareTo(comment2.getRank());
                }
                return new Integer(comment1.hashCode()).compareTo(comment2.hashCode());
            }
        });
        return comments;
    }

    public boolean isManuallySetTranslationEnd(CDS cds) {

        for (Comment comment : getComments(cds)) {
            if (comment.value.equals(MANUALLY_SET_TRANSLATION_END)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate the longest ORF for a transcript.  If a valid start codon is not found, allow for partial CDS start/end.
     * Calls setLongestORF(Transcript, TranslationTable, boolean) with the translation table and whether partial
     * ORF calculation extensions are allowed from the configuration associated with this editor.
     *
     * @param transcript - Transcript to set the longest ORF to
     */
    public void setLongestORF(Transcript transcript, boolean readThroughStopCodon) {
        setLongestORF(transcript, configWrapperService.getTranslationTable(), false, readThroughStopCodon);
    }

    public void setLongestORF(Transcript transcript) {
        setLongestORF(transcript, false);
    }

    /**
     * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript       - Transcript to set the translation start in
     * @param translationStart - Coordinate of the start of translation
     */
    public void setTranslationStart(Transcript transcript, int translationStart) {
        setTranslationStart(transcript, translationStart, false);
    }

    /**
     * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript        - Transcript to set the translation start in
     * @param translationStart  - Coordinate of the start of translation
     * @param setTranslationEnd - if set to true, will search for the nearest in frame stop codon
     */
    public void setTranslationStart(Transcript transcript, int translationStart, boolean setTranslationEnd) {
        setTranslationStart(transcript, translationStart, setTranslationEnd, false);
    }

    /**
     * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript           - Transcript to set the translation start in
     * @param translationStart     - Coordinate of the start of translation
     * @param setTranslationEnd    - if set to true, will search for the nearest in frame stop codon
     * @param readThroughStopCodon - if set to true, will read through the first stop codon to the next
     */
    public void setTranslationStart(Transcript transcript, int translationStart, boolean setTranslationEnd, boolean readThroughStopCodon) {
        setTranslationStart(transcript, translationStart, setTranslationEnd, setTranslationEnd ? configWrapperService.getTranslationTable() : null, readThroughStopCodon);
    }


    /** Convert local coordinate to source feature coordinate.
     *
     * @param localCoordinate - Coordinate to convert to source coordinate
     * @return Source feature coordinate, -1 if local coordinate is longer than feature's length or negative
     */
    public int convertLocalCoordinateToSourceCoordinate(Feature feature,int localCoordinate) {
        if (localCoordinate < 0 || localCoordinate > feature.getLength()) {
            return -1;
        }
        if (feature.getFeatureLocation().getStrand() == -1) {
            return feature.getFeatureLocation().getFmax() - localCoordinate - 1;
        }
        else {
            return feature.getFeatureLocation().getFmin() + localCoordinate;
        }
    }

    public int convertModifiedLocalCoordinateToSourceCoordinate(Feature feature,
                                                                int localCoordinate) {
        Transcript transcript = cdsService.getTranscript((CDS) feature )
        List<SequenceAlteration> alterations = feature instanceof CDS ? getFrameshiftsAsAlterations(transcript) : new ArrayList<SequenceAlteration>();


//        alterations.addAll(dataStore.getSequenceAlterations());
        alterations.addAll(getAllSequenceAlterationsForFeature(feature))
        if (alterations.size() == 0) {
            return convertLocalCoordinateToSourceCoordinate(feature,localCoordinate);
        }
//        Collections.sort(alterations, new BioObjectUtil.FeaturePositionComparator<SequenceAlteration>());
        Collections.sort(alterations, new FeaturePositionComparator<SequenceAlteration>());
        if (feature.getFeatureLocation().getStrand() == -1) {
            Collections.reverse(alterations);
        }
        for (SequenceAlteration alteration : alterations) {
            if (!overlaps(feature,alteration)) {
                continue;
            }
            if (feature.getFeatureLocation().getStrand() == -1) {
                if (convertSourceCoordinateToLocalCoordinate(feature,alteration.getFeatureLocation().getFmin()) > localCoordinate) {
                    localCoordinate -= alteration.getOffset();
                }
            }
            else {
                if (convertSourceCoordinateToLocalCoordinate(feature,alteration.getFeatureLocation().getFmin()) < localCoordinate) {
                    localCoordinate -= alteration.getOffset();
                }
            }
        }
        return convertLocalCoordinateToSourceCoordinate(feature,localCoordinate);
    }


    /**
     * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript           - Transcript to set the translation start in
     * @param translationStart     - Coordinate of the start of translation
     * @param setTranslationEnd    - if set to true, will search for the nearest in frame stop codon
     * @param translationTable     - Translation table that defines the codon translation
     * @param readThroughStopCodon - if set to true, will read through the first stop codon to the next
     */
    public void setTranslationStart(Transcript transcript, int translationStart, boolean setTranslationEnd, TranslationTable translationTable, boolean readThroughStopCodon) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            featureRelationshipService.addChildFeature(transcript,cds)
//            transcript.setCDS(cds);
        }
        if (transcript.getStrand() == -1) {
            setFmax(cds,translationStart + 1);
        } else {
            setFmin(cds,translationStart);
        }
        cdsService.setManuallySetTranslationStart(cds, true);
//        cds.deleteStopCodonReadThrough();
        cdsService.deleteStopCodonReadThrough(cds);
//        featureRelationshipService.deleteRelationships()
        if (setTranslationEnd && translationTable != null) {
            String mrna = getResiduesWithAlterationsAndFrameshifts(transcript);
            if (mrna == null || mrna.equals("null")) {
                return;
            }
            int stopCodonCount = 0;
            for (int i = convertSourceCoordinateToLocalCoordinate(transcript,translationStart); i < transcript.getLength(); i += 3) {
                if (i + 3 > mrna.length()) {
                    break;
                }
                String codon = mrna.substring(i, i + 3);
                if (translationTable.getStopCodons().contains(codon)) {
                    if (readThroughStopCodon && ++stopCodonCount < 2) {
                        StopCodonReadThrough stopCodonReadThrough = cdsService.getStopCodonReadThrough(cds);
                        if (stopCodonReadThrough == null) {
                            stopCodonReadThrough = cdsService.createStopCodonReadThrough(cds);
                            cdsService.setStopCodonReadThrough(cds,stopCodonReadThrough)
//                            cds.setStopCodonReadThrough(stopCodonReadThrough);
                            if (cds.getStrand() == -1) {
                                stopCodonReadThrough.featureLocation.setFmin(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i + 3));
                                stopCodonReadThrough.featureLocation.setFmax(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i) + 1);
                            } else {
                                stopCodonReadThrough.featureLocation.setFmin(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i));
                                stopCodonReadThrough.featureLocation.setFmax(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i + 3) + 1);
                            }
                        }
                        continue;
                    }
                    if (transcript.getStrand() == -1) {
                        cds.featureLocation.setFmin(convertLocalCoordinateToSourceCoordinate(transcript,i + 2));
                    } else {
                        cds.featureLocation.setFmax(convertLocalCoordinateToSourceCoordinate(transcript,i + 3));
                    }
                    return;
                }
            }
            if (transcript.getStrand() == -1) {
                cds.featureLocation.setFmin(transcript.getFmin());
                cds.featureLocation.setIsFminPartial(true);
            } else {
                cds.featureLocation.setFmax(transcript.getFmax());
                cds.featureLocation.setIsFmaxPartial(true);
            }
        }

        Date date = new Date();
        cds.setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    /** Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
     *  Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript - Transcript to set the translation start in
     * @param translationEnd - Coordinate of the end of translation
     */
    /*
    public void setTranslationEnd(Transcript transcript, int translationEnd) {
        CDS cds = transcript.getCDS();
        if (cds == null) {
            cds = createCDS(transcript);
            transcript.setCDS(cds);
        }
        if (transcript.getStrand() == -1) {
            cds.setFmin(translationEnd + 1);
        }
        else {
            cds.setFmax(translationEnd);
        }
        setManuallySetTranslationEnd(cds, true);
        cds.deleteStopCodonReadThrough();

        // event fire
        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }
    */

    /**
     * Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript     - Transcript to set the translation end in
     * @param translationEnd - Coordinate of the end of translation
     */
    public void setTranslationEnd(Transcript transcript, int translationEnd) {
        setTranslationEnd(transcript, translationEnd, false);
    }

    /**
     * Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript          - Transcript to set the translation end in
     * @param translationEnd      - Coordinate of the end of translation
     * @param setTranslationStart - if set to true, will search for the nearest in frame start
     */
    public void setTranslationEnd(Transcript transcript, int translationEnd, boolean setTranslationStart) {
        setTranslationEnd(transcript, translationEnd, setTranslationStart ,
                setTranslationStart ? configWrapperService.getTranslationTable() : null
        );
    }

    public void setManuallySetTranslationEnd(CDS cds, boolean manuallySetTranslationEnd) {
        if (manuallySetTranslationEnd && isManuallySetTranslationEnd(cds)) {
            return;
        }
        if (!manuallySetTranslationEnd && !isManuallySetTranslationEnd(cds)) {
            return;
        }
        if (manuallySetTranslationEnd) {
            addComment(cds,MANUALLY_SET_TRANSLATION_END)
        }
        if (!manuallySetTranslationEnd) {
            deleteComment(cds,MANUALLY_SET_TRANSLATION_END)
        }
    }

    /**
     * Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
     * Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript          - Transcript to set the translation end in
     * @param translationEnd      - Coordinate of the end of translation
     * @param setTranslationStart - if set to true, will search for the nearest in frame start codon
     * @param translationTable    - Translation table that defines the codon translation
     */
    public void setTranslationEnd(Transcript transcript, int translationEnd, boolean setTranslationStart, TranslationTable translationTable) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript,cds);
        }
        if (transcript.getStrand() == -1) {
            cds.featureLocation.setFmin(translationEnd);
        } else {
            cds.featureLocation.setFmax(translationEnd + 1);
        }
        setManuallySetTranslationEnd(cds, true);
        cdsService.deleteStopCodonReadThrough(cds);
        if (setTranslationStart && translationTable != null) {
            String mrna = getResiduesWithAlterationsAndFrameshifts(transcript);
            if (mrna == null || mrna.equals("null")) {
                return;
            }
            for (int i = convertSourceCoordinateToLocalCoordinate(transcript,translationEnd) - 3; i >= 0; i -= 3) {
                if (i - 3 < 0) {
                    break;
                }
                String codon = mrna.substring(i, i + 3);
                if (translationTable.getStartCodons().contains(codon)) {
                    if (transcript.getStrand() == -1) {
                        cds.featureLocation.setFmax(convertLocalCoordinateToSourceCoordinate(transcript,i + 3));
                    } else {
                        cds.featureLocation.setFmin(convertLocalCoordinateToSourceCoordinate(transcript,i + 2));
                    }
                    return;
                }
            }
            if (transcript.getStrand() == -1) {
                cds.featureLocation.setFmin(transcript.getFmin());
                cds.featureLocation.setIsFminPartial(true);
            } else {
                cds.featureLocation.setFmax(transcript.getFmax());
                cds.featureLocation.setIsFmaxPartial(true);
            }
        }

        Date date = new Date();
        cds.setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire TODO: ??
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    void setTranslationFmin(Transcript transcript, int translationFmin) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript,cds);
        }
        setFmin(cds,translationFmin);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    void setTranslationFmax(Transcript transcript, int translationFmax) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript,cds);
        }
        setFmax(cds,translationFmax);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    /**
     * Set the translation start and end in the transcript.  Sets the translation start and end in the underlying CDS
     * feature.  Instantiates the CDS object for the transcript if it doesn't already exist.
     *
     * @param transcript       - Transcript to set the translation start in
     * @param translationStart - Coordinate of the start of translation
     * @param translationEnd   - Coordinate of the end of translation
     * @param manuallySetStart - whether the start was manually set
     * @param manuallySetEnd   - whether the end was manually set
     */
    public void setTranslationEnds(Transcript transcript, int translationStart, int translationEnd, boolean manuallySetStart, boolean manuallySetEnd) {
        setTranslationFmin(transcript, translationStart);
        setTranslationFmax(transcript, translationEnd);
        setManuallySetTranslationStart(transcriptService.getCDS(transcript), manuallySetStart);
        setManuallySetTranslationEnd(transcriptService.getCDS(transcript), manuallySetEnd);

        Date date = new Date();
        transcriptService.getCDS(transcript).setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    public void setManuallySetTranslationStart(CDS cds, boolean manuallySetTranslationStart) {
        if (manuallySetTranslationStart && isManuallySetTranslationStart(cds)) {
            return;
        }
        if (!manuallySetTranslationStart && !isManuallySetTranslationStart(cds)) {
            return;
        }
        if (manuallySetTranslationStart) {
            addComment(cds,MANUALLY_SET_TRANSLATION_START)
        }
        if (!manuallySetTranslationStart) {
            deleteComment(cds,MANUALLY_SET_TRANSLATION_START)
        }
    }

    public void setLongestORF(Transcript transcript, TranslationTable translationTable, boolean allowPartialExtension) {
        setLongestORF(transcript, translationTable, allowPartialExtension, false);
    }

    /** Get the residues for a feature with any alterations and frameshifts.
     *
     * @param feature - AbstractSingleLocationBioFeature to retrieve the residues for
     * @return Residues for the feature with any alterations and frameshifts
     */
    public String getResiduesWithAlterationsAndFrameshifts(Feature feature) {
        if (!(feature instanceof CDS)) {
            return getResiduesWithAlterations(feature,SequenceAlteration.all);
        }
        Transcript transcript = cdsService.getTranscript((CDS) feature)
        Collection<SequenceAlteration> alterations = getFrameshiftsAsAlterations(transcript);

        List<SequenceAlteration> allSequenceAlterationList = getAllSequenceAlterationsForFeature(feature)

//        List<SequenceAlteration> sequenceAlterationList = sequences.featureLocations.
//        alterations.addAll(dataStore.getSequenceAlterations());
        alterations.addAll(allSequenceAlterationList);
        return getResiduesWithAlterations(feature, alterations);
    }

    List<SequenceAlteration> getAllSequenceAlterationsForFeature(Feature feature) {
        List<Sequence> sequences = feature.featureLocations*.sequence
        List<SequenceAlteration> allSequenceAlterationList = SequenceAlteration.executeQuery(
                "select sa from  SequenceAlteration sa where sa.featureLocation.sequence in (:sequences) "
                ,[sequences:sequences])
        return allSequenceAlterationList
    }

    List<SequenceAlteration> getFrameshiftsAsAlterations(Transcript transcript) {
        List<SequenceAlteration> frameshifts = new ArrayList<SequenceAlteration>();
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            return frameshifts;
        }
//        AbstractBioFeature sourceFeature =
//                (AbstractBioFeature)BioObjectUtil.createBioObject(cds.getFeatureLocation().getSourceFeature(),
//                        cds.getConfiguration());
        Feature sourceFeature =cds.getFeatureLocation().getSourceFeature()
        List<Frameshift> frameshiftList = transcriptService.getFrameshifts(transcript)
        for (Frameshift frameshift : frameshiftList) {
            if (frameshift.isPlusFrameshift()) {
                // a plus frameshift skips bases during translation, which can be mapped to a deletion for the
                // the skipped bases
//                Deletion deletion = new Deletion(cds.getOrganism(), "Deletion-" + frameshift.getCoordinate(), false,
//                        false, new Timestamp(new Date().getTime()), cds.getConfiguration());

                FeatureLocation featureLocation = new FeatureLocation(
                        fmin:frameshift.coordinate
                        ,fmax: frameshift.coordinate + frameshift.frameshiftValue
                        ,strand: cds.featureLocation.strand
                        ,sourceFeature: sourceFeature
                )

                Deletion deletion = new Deletion(
                        organism: cds.organism
                        ,uniqueName:FeatureStringEnum.DELETION_PREFIX.value+frameshift.coordinate
                        ,isObsolete: false
                        ,isAnalysis: false
                )

                featureLocation.feature = deletion
                deletion.featureLocation = featureLocation

                frameshifts.add(deletion);
                featureLocation.save()
                deletion.save()
                frameshift.save(flush: true)

//                deletion.setFeatureLocation(frameshift.getCoordinate(),
//                        frameshift.getCoordinate() + frameshift.getFrameshiftValue(),
//                        cds.getFeatureLocation().getStrand(), sourceFeature);


            }
            else {
                // a minus frameshift goes back bases during translation, which can be mapped to an insertion for the
                // the repeated bases
                Insertion insertion = new Insertion(
                        organism: cds.organism
                        ,uniqueName: FeatureStringEnum.INSERTION_PREFIX.value + frameshift.coordinate
                        ,isAnalysis: false
                        ,isObsolete: false
                ).save()


//                Insertion insertion = new Insertion(cds.getOrganism(), "Insertion-" + frameshift.getCoordinate(), false,
//                        false, new Timestamp(new Date().getTime()), cds.getConfiguration());

                FeatureLocation featureLocation = new FeatureLocation(
                        fmin: frameshift.coordinate
                        ,fmax: frameshift.coordinate + frameshift.frameshiftValue
                        ,strand: cds.featureLocation.strand
                        ,sourceFeature: sourceFeature
                ).save()

                insertion.featureLocation = featureLocation
                featureLocation.feature = insertion

//                insertion.setFeatureLocation(frameshift.getCoordinate() + frameshift.getFrameshiftValue(),
//                        frameshift.getCoordinate() + frameshift.getFrameshiftValue(),
//                        cds.getFeatureLocation().getStrand(), sourceFeature);
                insertion.setResidues(sourceFeature.getResidues().substring(
                        frameshift.getCoordinate() + frameshift.getFrameshiftValue(), frameshift.getCoordinate()));
                frameshifts.add(insertion);

                insertion.save()
                featureLocation.save()
                frameshift.save(flush: true)
            }
        }
        return frameshifts;
    }


    /**
     * Calculate the longest ORF for a transcript.  If a valid start codon is not found, allow for partial CDS start/end.
     *
     * @param transcript            - Transcript to set the longest ORF to
     * @param translationTable      - Translation table that defines the codon translation
     * @param allowPartialExtension - Where partial ORFs should be used for possible extension
     */
    public void setLongestORF(Transcript transcript, TranslationTable translationTable, boolean allowPartialExtension, boolean readThroughStopCodon) {
        String mrna = getResiduesWithAlterationsAndFrameshifts(transcript);
        if (mrna == null || mrna.equals("null")) {
            return;
        }
        String longestPeptide = "";
        int bestStartIndex = -1;
        int bestStopIndex = -1;
        boolean partialStop = false;

        if (mrna.length() > 3) {
            for (String startCodon : translationTable.getStartCodons()) {
                int startIndex = mrna.indexOf(startCodon);
                while (startIndex >= 0) {
                    String mrnaSubstring = mrna.substring(startIndex);
                    String aa = SequenceTranslationHandler.translateSequence(mrnaSubstring, translationTable, true, readThroughStopCodon);
                    if (aa.length() > longestPeptide.length()) {
                        longestPeptide = aa;
                        bestStartIndex = startIndex;
                        bestStopIndex = startIndex + (aa.length() * 3);
                        if (!longestPeptide.substring(longestPeptide.length() - 1).equals(TranslationTable.STOP)) {
                            partialStop = true;
                            bestStopIndex += mrnaSubstring.length() % 3;
                        }
                    }
                    startIndex = mrna.indexOf(startCodon, startIndex + 1);
                }
            }
        }

        CDS cds = transcriptService.getCDS(transcript)
        boolean needCdsIndex = cds == null;
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript,cds);
        }
        if (bestStartIndex >= 0) {
            int fmin = convertModifiedLocalCoordinateToSourceCoordinate(transcript, bestStartIndex);
            int fmax = convertModifiedLocalCoordinateToSourceCoordinate(transcript, bestStopIndex);
            if (cds.getStrand().equals(-1)) {
                int tmp = fmin;
                fmin = fmax + 1;
                fmax = tmp + 1;
            }
            setFmin(cds,fmin);
            cds.featureLocation.setIsFminPartial(false);
            setFmax(cds,fmax);
            cds.featureLocation.setIsFmaxPartial(partialStop);
        } else {
            setFmin(cds,transcript.getFmin());
            cds.featureLocation.setIsFminPartial(true);
            String aa = SequenceTranslationHandler.translateSequence(mrna, translationTable, true, readThroughStopCodon);
            if (aa.substring(aa.length() - 1).equals(TranslationTable.STOP)) {
                setFmax(cds,convertModifiedLocalCoordinateToSourceCoordinate(transcript, aa.length() * 3));
                cds.featureLocation.setIsFmaxPartial(false);
            } else {
                setFmax(cds,transcript.getFmax());
                cds.featureLocation.setIsFmaxPartial(true);
            }
        }
        if (readThroughStopCodon) {
            String aa = SequenceTranslationHandler.translateSequence(getResiduesWithAlterationsAndFrameshifts(cds), translationTable, true, true);
            int firstStopIndex = aa.indexOf(TranslationTable.STOP);
            if (firstStopIndex < aa.length() - 1) {
                StopCodonReadThrough stopCodonReadThrough = cdsService.createStopCodonReadThrough(cds);
                cdsService.setStopCodonReadThrough(cds,stopCodonReadThrough);
                int offset = transcript.getStrand() == -1 ? -2 : 0;
                setFmin(stopCodonReadThrough,convertModifiedLocalCoordinateToSourceCoordinate(cds, firstStopIndex * 3) + offset);
                setFmax(stopCodonReadThrough,convertModifiedLocalCoordinateToSourceCoordinate(cds, firstStopIndex * 3) + 3 + offset);
            }
        } else {
            cdsService.deleteStopCodonReadThrough(cds);
        }
        setManuallySetTranslationStart(cds, false);
        setManuallySetTranslationEnd(cds, false);

//        if (needCdsIndex) {
//            getSession().indexFeature(cds);
//        }

        Date date = new Date();
        cds.setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationEditor.AnnotationChangeEvent.Operation.UPDATE);

    }

    /**
     * TODO: not srue if this is legit.
     * @param jsonObject
     * @param featureLazyResidues
     * @return
     */
    Feature convertJSONToFeature(JSONObject jsonObject, FeatureLazyResidues featureLazyResidues) {
        Organism organism = featureLazyResidues.getOrganism()
        return convertJSONToFeature(jsonObject,organism,featureLazyResidues)
    }

    public Feature convertJSONToFeature(JSONObject jsonFeature, Organism organism, Feature sourceFeature) {
        Feature gsolFeature = new Feature();
        try {
            gsolFeature.setOrganism(organism);
            JSONObject type = jsonFeature.getJSONObject("type");
            gsolFeature.setType(cvTermService.convertJSONToCVTerm(type));
            if (jsonFeature.has("uniquename")) {
                gsolFeature.setUniqueName(jsonFeature.getString("uniquename"));
            }
            if (jsonFeature.has("name")) {
                gsolFeature.setName(jsonFeature.getString("name"));
                gsolFeature.setUniqueName(nameService.generateUniqueName(gsolFeature));
            }
            else{
                gsolFeature.setUniqueName(nameService.generateUniqueName());
            }
            if (jsonFeature.has("residues")) {
                gsolFeature.setResidues(jsonFeature.getString("residues"));
            }
            if (jsonFeature.has("location")) {
                JSONObject jsonLocation = jsonFeature.getJSONObject("location");
                gsolFeature.addToFeatureLocations(convertJSONToFeatureLocation(jsonLocation, sourceFeature));
            }
            if (jsonFeature.has("children")) {
                JSONArray children = jsonFeature.getJSONArray("children");
                CVTerm partOfCvTerm = CVTerm.findByName("PartOf")
                for (int i = 0; i < children.length(); ++i) {
                    Feature child = convertJSONToFeature(children.getJSONObject(i),  sourceFeature != null ? sourceFeature.getOrganism() : null, sourceFeature);
                    FeatureRelationship fr = new FeatureRelationship();
                    fr.setObjectFeature(gsolFeature);
                    fr.setSubjectFeature(child);
//                    fr.setType(configuration.getDefaultCVTermForClass("PartOf"));
                    fr.setType(partOfCvTerm);
                    child.getParentFeatureRelationships().add(fr);
                    gsolFeature.getChildFeatureRelationships().add(fr);
                }
            }
            if (jsonFeature.has("timeaccessioned")) {
                gsolFeature.setDateCreated(new Date(jsonFeature.getInt("timeaccessioned")));
            } else {
                gsolFeature.setDateCreated(new Date());
            }
            if (jsonFeature.has("timelastmodified")) {
                gsolFeature.setLastUpdated(new Date(jsonFeature.getInt("timelastmodified")));
            } else {
                gsolFeature.setLastUpdated(new Date());
            }
            if (jsonFeature.has("properties")) {
                JSONArray properties = jsonFeature.getJSONArray("properties");
                for (int i = 0; i < properties.length(); ++i) {
                    JSONObject property = properties.getJSONObject(i);
                    JSONObject propertyType = property.getJSONObject("type");
                    FeatureProperty gsolProperty = new FeatureProperty();
                    CV cv = CV.findByName(propertyType.getJSONObject(FeatureStringEnum.CV.value).getString(FeatureStringEnum.NAME.value))
                    CVTerm cvTerm = CVTerm.findByNameAndCv(propertyType.getString(FeatureStringEnum.NAME.value),cv)
//                    gsolProperty.setType(new CVTerm(propertyType.getString("name"), new CV(propertyType.getJSONObject("cv").getString("name"))));
                    gsolProperty.setType(cvTerm);
                    gsolProperty.setValue(property.getString(FeatureStringEnum.VALUE.value));
                    gsolProperty.addToFeatures(gsolFeature);
                    int rank = 0;
                    for (FeatureProperty fp : gsolFeature.getFeatureProperties()) {
                        if (fp.getType().equals(gsolProperty.getType())) {
                            if (fp.getRank() > rank) {
                                rank = fp.getRank();
                            }
                        }
                    }
                    gsolProperty.setRank(rank + 1);
                    gsolFeature.getFeatureProperties().add(gsolProperty);
                }
            }
            if (jsonFeature.has(FeatureStringEnum.DBXREFS.value)) {
                JSONArray dbxrefs = jsonFeature.getJSONArray(FeatureStringEnum.DBXREFS.value);
                for (int i = 0; i < dbxrefs.length(); ++i) {
                    JSONObject dbxref = dbxrefs.getJSONObject(i);
                    JSONObject db = dbxref.getJSONObject(FeatureStringEnum.DB.value);


                    DB newDB = DB.findOrSaveByName(db.getString(FeatureStringEnum.NAME.value))
                    DBXref newDBXref = DBXref.findOrSaveByDbAndAccession(
                            newDB
                            ,dbxref.getString(FeatureStringEnum.ACCESSION.value)
                    )
                    FeatureDBXref featureDBXref = new FeatureDBXref(
                            feature: gsolFeature
                            ,dbxref: newDBXref
                    ).save()
                    gsolFeature.addToFeatureDBXrefs(
                            featureDBXref
                    ).save()
//                    gsolFeature.addFeatureDBXref(new DB(db.getString("name")), dbxref.getString(FeatureStringEnum.ACCESSION.value));
                }
            }
        }
        catch (JSONException e) {
            return null;
        }
        return gsolFeature;
    }


//    public CVTerm convertJSONToCVTerm(JSONObject jsonCVTerm) throws JSONException {
//        return new CVTerm(jsonCVTerm.getString("name"), new CV(jsonCVTerm.getJSONObject("cv").getString("name")));
//    }

    void updateGeneBoundaries(Gene gene) {
        if (gene == null) {
            return;
        }
        int geneFmax = Integer.MIN_VALUE;
        int geneFmin = Integer.MAX_VALUE;
        for (Transcript t : transcriptService.getTranscripts(gene)) {
            if (t.getFmin() < geneFmin) {
                geneFmin = t.getFmin();
            }
            if (t.getFmax() > geneFmax) {
                geneFmax = t.getFmax();
            }
        }
        gene.featureLocation.setFmin(geneFmin);
        gene.featureLocation.setFmax(geneFmax);
        gene.setLastUpdated(new Date());
    }

    def setFmin(Feature feature, int fmin) {
        feature.getFeatureLocation().setFmin(fmin);
    }

    def setFmax(Feature feature, int fmax) {
        feature.getFeatureLocation().setFmax(fmax);
    }


    /** Convert source feature coordinate to local coordinate.
     *
     * @param sourceCoordinate - Coordinate to convert to local coordinate
     * @return Local coordinate, -1 if source coordinate is <= fmin or >= fmax
     */
    public int convertSourceCoordinateToLocalCoordinate(Feature feature,int sourceCoordinate) {
        if (sourceCoordinate < feature.getFeatureLocation().getFmin() || sourceCoordinate > feature.getFeatureLocation().getFmax()) {
            return -1;
        }
        if (feature.getFeatureLocation().getStrand() == -1) {
            return feature.getFeatureLocation().getFmax() - 1 - sourceCoordinate;
        }
        else {
            return sourceCoordinate - feature.getFeatureLocation().getFmin();
        }
    }

//    public String getResiduesWithAlterations(Feature feature) {
//        return getResiduesWithAlterations(feature, SequenceAlteration.all);
//    }

    String getResiduesWithAlterations(Feature feature,
                                              Collection<SequenceAlteration> sequenceAlterations = new ArrayList<>()) {
        if (sequenceAlterations.size() == 0) {
            return feature.getResidues();
        }
        StringBuilder residues = new StringBuilder(feature.getResidues());
        FeatureLocation featureLoc = feature.getFeatureLocation();
//        List<SequenceAlteration> orderedSequenceAlterationList = BioObjectUtil.createSortedFeatureListByLocation(sequenceAlterations);

        List<SequenceAlteration> orderedSequenceAlterationList = new ArrayList<>(sequenceAlterations)
        Collections.sort(orderedSequenceAlterationList, new FeaturePositionComparator<SequenceAlteration>());
        if (!feature.getFeatureLocation().getStrand().equals(orderedSequenceAlterationList.get(0).getFeatureLocation().getStrand())) {
            Collections.reverse(orderedSequenceAlterationList);
        }
        int currentOffset = 0;
        for (SequenceAlteration sequenceAlteration : orderedSequenceAlterationList) {
            if(!overlaps(feature,sequenceAlteration,false)){

            }
//            if (!feature.overlaps(sequenceAlteration, false)) {
//                continue;
//            }
            FeatureLocation sequenceAlterationLoc = sequenceAlteration.getFeatureLocation();
            if (sequenceAlterationLoc.getSourceFeature().equals(featureLoc.getSourceFeature())) {
                int localCoordinate = convertSourceCoordinateToLocalCoordinate(feature,sequenceAlterationLoc.getFmin());
                String sequenceAlterationResidues = sequenceAlteration.getResidues();
                if (feature.getFeatureLocation().getStrand() == -1) {
                    sequenceAlterationResidues = SequenceTranslationHandler.reverseComplementSequence(sequenceAlterationResidues);
                }
                // Insertions
                if (sequenceAlteration instanceof Insertion) {
                    if (feature.getFeatureLocation().getStrand() == -1) {
                        ++localCoordinate;
                    }
                    residues.insert(localCoordinate + currentOffset, sequenceAlterationResidues);
                    currentOffset += sequenceAlterationResidues.length();
                }
                // Deletions
                else if (sequenceAlteration instanceof Deletion) {
                    if (feature.getFeatureLocation().getStrand() == -1) {
                        residues.delete(localCoordinate + currentOffset - sequenceAlteration.getLength() + 1,
                                localCoordinate + currentOffset + 1);
                    }
                    else {
                        residues.delete(localCoordinate + currentOffset,
                                localCoordinate + currentOffset + sequenceAlteration.getLength());
                    }
                    currentOffset -= sequenceAlterationResidues.length();
                }
                // Substitions
                else if (sequenceAlteration instanceof Substitution) {
                    int start = feature.getStrand() == -1 ? localCoordinate - (sequenceAlteration.getLength() - 1) : localCoordinate;
                    residues.replace(start + currentOffset,
                            start + currentOffset + sequenceAlteration.getLength(),
                            sequenceAlterationResidues);
                }
            }
        }
        return residues.toString();
    }

    def addComment(Feature feature, Comment comment) {
        addProperty(feature,comment)
    }

    def addComment(Feature feature, String commentString) {
        Comment comment = new Comment(
                feature: feature
                ,type: cvTermService.getTerm(FeatureStringEnum.COMMENT.value)
                ,value: commentString
        ).save()

        addComment(feature,comment)
    }

    boolean deleteComment(Feature feature, String commentString) {
        CVTerm commentCVTerm = cvTermService.getTerm(FeatureStringEnum.COMMENT.value)
        Comment comment =  Comment.findByTypeAndFeatureAndValue(commentCVTerm,feature,commentString)
        if(comment){
            Comment.deleteAll(comment)
            return true
        }
        return false
    }
}