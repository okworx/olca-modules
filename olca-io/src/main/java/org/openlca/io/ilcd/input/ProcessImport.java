package org.openlca.io.ilcd.input;

import java.util.Date;
import java.util.List;

import org.openlca.core.database.IDatabase;
import org.openlca.core.model.Actor;
import org.openlca.core.model.AllocationMethod;
import org.openlca.core.model.Category;
import org.openlca.core.model.Location;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Process;
import org.openlca.core.model.ProcessDocumentation;
import org.openlca.core.model.ProcessType;
import org.openlca.core.model.Source;
import org.openlca.ilcd.commons.CommissionerAndGoal;
import org.openlca.ilcd.commons.DataSetReference;
import org.openlca.ilcd.commons.LCIMethodApproach;
import org.openlca.ilcd.io.DataStore;
import org.openlca.ilcd.processes.DataEntry;
import org.openlca.ilcd.processes.Geography;
import org.openlca.ilcd.processes.LCIMethod;
import org.openlca.ilcd.processes.Publication;
import org.openlca.ilcd.processes.Representativeness;
import org.openlca.ilcd.processes.Review;
import org.openlca.ilcd.util.LangString;
import org.openlca.ilcd.util.ProcessBag;
import org.openlca.io.KeyGen;
import org.openlca.io.maps.FlowMap;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessImport {

	private Logger log = LoggerFactory.getLogger(getClass());
	private IDatabase database;
	private DataStore dataStore;
	private ProcessBag ilcdProcess;
	private Process process;
	private FlowMap flowMap;
	private ProcessDocumentation documentation;

	public ProcessImport(DataStore dataStore, IDatabase database) {
		this.database = database;
		this.dataStore = dataStore;
	}

	public void setFlowMap(FlowMap flowMap) {
		this.flowMap = flowMap;
	}

	public Process run(org.openlca.ilcd.processes.Process process)
			throws ImportException {
		this.ilcdProcess = new ProcessBag(process);
		Process oProcess = findExisting(ilcdProcess.getId());
		if (oProcess != null)
			return oProcess;
		return createNew();
	}

	public Process run(String processId) throws ImportException {
		Process process = findExisting(processId);
		if (process != null)
			return process;
		org.openlca.ilcd.processes.Process iProcess = tryGetProcess(processId);
		ilcdProcess = new ProcessBag(iProcess);
		return createNew();
	}

	private Process findExisting(String processId) throws ImportException {
		try {
			return database.createDao(Process.class).getForId(processId);
		} catch (Exception e) {
			String message = String.format("Search for process %s failed.",
					processId);
			throw new ImportException(message, e);
		}
	}

	private Process createNew() throws ImportException {
		process = new Process();
		importAndSetCategory();
		createAndMapContent();
		saveInDatabase(process);
		return process;
	}

	private org.openlca.ilcd.processes.Process tryGetProcess(String processId)
			throws ImportException {
		try {
			org.openlca.ilcd.processes.Process iProcess = dataStore.get(
					org.openlca.ilcd.processes.Process.class, processId);
			if (iProcess == null) {
				throw new ImportException("No ILCD process for ID " + processId
						+ " found");
			}
			return iProcess;
		} catch (Exception e) {
			throw new ImportException(e.getMessage(), e);
		}
	}

	private void importAndSetCategory() throws ImportException {
		CategoryImport categoryImport = new CategoryImport(database,
				ModelType.PROCESS);
		Category category = categoryImport.run(ilcdProcess.getSortedClasses());
		process.setCategory(category);
	}

	private void createAndMapContent() throws ImportException {
		process.setId(ilcdProcess.getId());
		process.setName(Strings.cut(ilcdProcess.getName(), 254));
		process.setDescription(ilcdProcess.getComment());
		mapDocumentation();
		ProcessParameterConversion paramConv = new ProcessParameterConversion(
				process, database);
		paramConv.run(ilcdProcess);
		ProcessExchanges.mapFrom(dataStore, ilcdProcess).withFlowMap(flowMap)
				.to(database, process);
	}

	private void mapDocumentation() throws ImportException {
		documentation = new ProcessDocumentation();
		documentation.setId(ilcdProcess.getId());
		ProcessTime processTime = new ProcessTime(ilcdProcess.getTime());
		processTime.map(documentation);
		mapGeography();
		mapTechnology();
		mapPublication();
		mapDataEntry();
		mapDataGenerator();
		mapComissionerAndGoal();
		mapLciMethod();
		mapRepresentativeness();
		mapReviews();
		saveInDatabase(documentation);
	}

	private void mapGeography() throws ImportException {
		Geography iGeography = ilcdProcess.getGeography();
		if (iGeography != null) {

			if (iGeography.getLocation() != null
					&& iGeography.getLocation().getLocation() != null) {
				String locationCode = iGeography.getLocation().getLocation();
				try {
					String locationId = KeyGen.get(locationCode);

					// find a location
					Location location = database.createDao(Location.class)
							.getForId(locationId);

					// create a new location
					if (location == null) {
						location = new Location();
						location.setCode(locationCode);
						location.setId(locationId);
						location.setName(locationCode);
						database.createDao(Location.class).insert(location);
					}

					process.setLocation(location);
				} catch (Exception e) {
					throw new ImportException(e);
				}
			}

			// comment
			if (iGeography.getLocation() != null) {
				documentation.setGeography(LangString.getFreeText(iGeography
						.getLocation().getDescription()));
			}

		}
	}

	private void mapTechnology() throws ImportException {
		org.openlca.ilcd.processes.Technology iTechnology = ilcdProcess
				.getTechnology();
		if (iTechnology != null) {
			documentation.setTechnology(LangString.getFreeText(iTechnology
					.getTechnologyDescriptionAndIncludedProcesses()));
		}
	}

	private void mapPublication() {
		Publication iPublication = ilcdProcess.getPublication();
		if (iPublication != null) {

			// data set owner
			DataSetReference ownerRef = iPublication
					.getReferenceToOwnershipOfDataSet();
			if (ownerRef != null)
				documentation.setDataSetOwner(fetchActor(ownerRef));

			// publication
			DataSetReference publicationRef = iPublication
					.getReferenceToUnchangedRepublication();
			if (publicationRef != null)
				documentation.setPublication(fetchSource(publicationRef));

			// access and use restrictions
			documentation.setRestrictions(LangString.getFreeText(iPublication
					.getAccessRestrictions()));

			// version
			documentation.setVersion(iPublication.getDataSetVersion());

			// copyright
			if (iPublication.isCopyright() != null) {
				documentation.setCopyright(iPublication.isCopyright());
			}

		}
	}

	private void mapDataEntry() {
		DataEntry iEntry = ilcdProcess.getDataEntry();
		if (iEntry != null) {

			// last change && creation date
			if (iEntry.getTimeStamp() != null) {
				Date tStamp = iEntry.getTimeStamp().toGregorianCalendar()
						.getTime();
				documentation.setCreationDate(tStamp);
				documentation.setLastChange(tStamp);
			}

			if (iEntry.getReferenceToPersonOrEntityEnteringTheData() != null) {
				Actor documentor = fetchActor(iEntry
						.getReferenceToPersonOrEntityEnteringTheData());
				documentation.setDataDocumentor(documentor);
			}
		}
	}

	private void mapDataGenerator() {
		if (ilcdProcess.getDataGenerator() != null) {
			List<DataSetReference> refs = ilcdProcess.getDataGenerator()
					.getReferenceToPersonOrEntityGeneratingTheDataSet();
			if (refs != null && !refs.isEmpty()) {
				DataSetReference generatorRef = refs.get(0);
				documentation.setDataGenerator(fetchActor(generatorRef));
			}
		}
	}

	private void mapComissionerAndGoal() {
		if (ilcdProcess.getCommissionerAndGoal() != null) {
			CommissionerAndGoal comAndGoal = ilcdProcess
					.getCommissionerAndGoal();
			String intendedApp = LangString.getFreeText(comAndGoal
					.getIntendedApplications());
			documentation.setIntendedApplication(intendedApp);
			String project = LangString.getLabel(comAndGoal.getProject());
			documentation.setProject(project);
		}
	}

	private void mapLciMethod() {

		if (ilcdProcess.getProcessType() != null) {
			switch (ilcdProcess.getProcessType()) {
			case UNIT_PROCESS_BLACK_BOX:
				process.setProcessType(ProcessType.UnitProcess);
				break;
			case UNIT_PROCESS_SINGLE_OPERATION:
				process.setProcessType(ProcessType.UnitProcess);
				break;
			default:
				process.setProcessType(ProcessType.LCI_Result);
				break;
			}
		}

		LCIMethod iMethod = ilcdProcess.getLciMethod();
		if (iMethod != null) {
			String lciPrinciple = LangString.getFreeText(iMethod
					.getDeviationsFromLCIMethodPrinciple());
			documentation.setInventoryMethod(lciPrinciple);
			documentation.setModelingConstants(LangString.getFreeText(iMethod
					.getModellingConstants()));
			process.setAllocationMethod(getAllocation(iMethod));
		}

	}

	private AllocationMethod getAllocation(LCIMethod iMethod) {
		List<LCIMethodApproach> approaches = iMethod.getLCIMethodApproaches();
		if (approaches == null || approaches.isEmpty())
			return null;
		for (LCIMethodApproach app : approaches) {
			switch (app) {
			case ALLOCATION_OTHER_EXPLICIT_ASSIGNMENT:
				return AllocationMethod.Causal;
			case ALLOCATION_MARKET_VALUE:
				return AllocationMethod.Economic;
			case ALLOCATION_PHYSICAL_CAUSALITY:
				return AllocationMethod.Physical;
			default:
				continue;
			}
		}
		return null;
	}

	private void mapRepresentativeness() {
		Representativeness iRepresentativeness = ilcdProcess
				.getRepresentativeness();
		if (iRepresentativeness != null) {

			// completeness
			documentation.setCompleteness(LangString
					.getFreeText(iRepresentativeness
							.getDataCutOffAndCompletenessPrinciples()));

			// data selection
			documentation.setDataSelection(LangString
					.getFreeText(iRepresentativeness
							.getDataSelectionAndCombinationPrinciples()));

			// data treatment
			documentation.setDataTreatment(LangString
					.getFreeText(iRepresentativeness
							.getDataTreatmentAndExtrapolationsPrinciples()));

			// sampling procedure
			documentation.setSampling(LangString
					.getFreeText(iRepresentativeness.getSamplingProcedure()));

			// data collection period
			documentation.setDataCollectionPeriod(LangString
					.getLabel(iRepresentativeness.getDataCollectionPeriod()));

			// data sources
			for (DataSetReference sourceRef : iRepresentativeness
					.getReferenceToDataSource()) {
				Source source = fetchSource(sourceRef);
				if (source != null)
					documentation.getSources().add(source);
			}

		}
	}

	private void mapReviews() {
		if (!ilcdProcess.getReviews().isEmpty()) {
			Review iReview = ilcdProcess.getReviews().get(0);
			// reviewer
			if (!iReview.getReferenceToNameOfReviewerAndInstitution().isEmpty()) {
				DataSetReference ref = iReview
						.getReferenceToNameOfReviewerAndInstitution().get(0);
				documentation.setReviewer(fetchActor(ref));
			}
			// review details
			documentation.setReviewDetails(LangString.getFreeText(iReview
					.getReviewDetails()));
		}
	}

	private Actor fetchActor(DataSetReference reference) {
		if (reference == null)
			return null;
		try {
			ContactImport contactImport = new ContactImport(dataStore, database);
			return contactImport.run(reference.getUuid());
		} catch (Exception e) {
			log.warn("Failed to get contact {} referenced from process {}",
					reference.getUuid(), process.getId());
			return null;
		}
	}

	private Source fetchSource(DataSetReference reference) {
		if (reference == null)
			return null;
		try {
			SourceImport sourceImport = new SourceImport(dataStore, database);
			return sourceImport.run(reference.getUuid());
		} catch (Exception e) {
			log.warn("Failed to get source {} referenced from process {}",
					reference.getUuid(), process.getId());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void saveInDatabase(T obj) throws ImportException {
		try {
			Class<T> clazz = (Class<T>) obj.getClass();
			database.createDao(clazz).insert(obj);
		} catch (Exception e) {
			String message = String.format(
					"Save operation failed in process %s.", process.getId());
			throw new ImportException(message, e);
		}
	}

}
