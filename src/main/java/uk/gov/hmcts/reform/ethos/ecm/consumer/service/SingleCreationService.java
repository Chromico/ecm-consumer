package uk.gov.hmcts.reform.ethos.ecm.consumer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ecm.common.client.CcdClient;
import uk.gov.hmcts.ecm.common.model.ccd.CCDRequest;
import uk.gov.hmcts.ecm.common.model.ccd.CaseData;
import uk.gov.hmcts.ecm.common.model.ccd.CaseDetails;
import uk.gov.hmcts.ecm.common.model.ccd.SubmitEvent;
import uk.gov.hmcts.ecm.common.model.servicebus.UpdateCaseMsg;
import uk.gov.hmcts.ecm.common.model.servicebus.datamodel.CreationSingleDataModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class SingleCreationService {

    private final CcdClient ccdClient;

    public void sendCreation(SubmitEvent oldSubmitEvent, String accessToken,
                             UpdateCaseMsg updateCaseMsg) throws IOException {

        CreationSingleDataModel creationSingleDataModel =
            ((CreationSingleDataModel) updateCaseMsg.getDataModelParent());
        String caseTypeId = creationSingleDataModel.getOfficeCT();
        String positionTypeCT = creationSingleDataModel.getPositionTypeCT();
        String ccdGatewayBaseUrl = creationSingleDataModel.getCcdGatewayBaseUrl();
        String jurisdiction = updateCaseMsg.getJurisdiction();
        String caseId = String.valueOf(oldSubmitEvent.getCaseId());

        log.info("Retrieve single case and check if exists");

        SubmitEvent caseDestinationOffice =
            existCaseDestinationOffice(accessToken, oldSubmitEvent.getCaseData().getEthosCaseReference(), caseTypeId);

        if (caseDestinationOffice != null) {

            log.info("Amend case state as it is returned");

            updateExistingCase(caseDestinationOffice, oldSubmitEvent, caseId, caseTypeId, jurisdiction, accessToken,
                               ccdGatewayBaseUrl, positionTypeCT);

        } else {

            log.info("Transferring new case");

            transferNewCase(oldSubmitEvent, caseId, caseTypeId, ccdGatewayBaseUrl, positionTypeCT,
                            jurisdiction, accessToken);

        }

    }

    private void updateExistingCase(SubmitEvent caseDestinationOffice, SubmitEvent oldSubmitEvent, String caseId,
                                    String caseTypeId, String jurisdiction, String accessToken,
                                    String ccdGatewayBaseUrl, String positionTypeCT) throws IOException {

        log.info("CaseDestinationOffice: " + caseDestinationOffice);
        log.info("Updating existing case: "
                     + caseId + " - "
                     + caseTypeId + " - "
                     + jurisdiction + " - "
                     + ccdGatewayBaseUrl + " - ");
        CCDRequest returnedRequest = ccdClient.returnCaseCreationTransfer(accessToken, caseTypeId, jurisdiction);

        ccdClient.submitEventForCase(accessToken,
                                     generateCaseDataCaseTransfer(caseDestinationOffice.getCaseData(),
                                                                  oldSubmitEvent.getCaseData(),
                                                                  caseId,
                                                                  ccdGatewayBaseUrl,
                                                                  positionTypeCT),
                                     caseTypeId,
                                     jurisdiction,
                                     returnedRequest,
                                     caseId);

    }

    private void transferNewCase(SubmitEvent oldSubmitEvent, String caseId, String caseTypeId, String ccdGatewayBaseUrl,
                                 String positionTypeCT, String jurisdiction, String accessToken) throws IOException {

        CaseDetails newCaseDetailsCT =
            createCaseDetailsCaseTransfer(oldSubmitEvent.getCaseData(), caseId, caseTypeId,
                                          ccdGatewayBaseUrl, positionTypeCT, jurisdiction);

        CCDRequest returnedRequest = ccdClient.startCaseCreationTransfer(accessToken, newCaseDetailsCT);

        ccdClient.submitCaseCreation(accessToken, newCaseDetailsCT, returnedRequest);

    }

    private SubmitEvent existCaseDestinationOffice(String accessToken, String ethosCaseReference,
                                                   String destinationCaseTypeId) throws IOException {

        List<SubmitEvent> submitEvents = retrieveDestinationCase(accessToken,
                                                                 ethosCaseReference, destinationCaseTypeId);

        return !submitEvents.isEmpty() ? submitEvents.get(0) : null;

    }

    private List<SubmitEvent> retrieveDestinationCase(String authToken, String ethosCaseReference,
                                                     String destinationCaseTypeId) throws IOException {

        return ccdClient.retrieveCasesElasticSearch(
            authToken,
            destinationCaseTypeId,
            new ArrayList<>(Collections.singletonList(ethosCaseReference)));

    }

    private CaseDetails createCaseDetailsCaseTransfer(CaseData oldCaseData, String caseId, String caseTypeId,
                                                      String ccdGatewayBaseUrl, String positionTypeCT,
                                                      String jurisdiction) {

        CaseDetails newCaseTransferCaseDetails = new CaseDetails();
        newCaseTransferCaseDetails.setCaseTypeId(caseTypeId);
        newCaseTransferCaseDetails.setJurisdiction(jurisdiction);
        newCaseTransferCaseDetails.setCaseData(
            generateNewCaseDataCaseTransfer(oldCaseData, caseId, ccdGatewayBaseUrl, positionTypeCT));
        return newCaseTransferCaseDetails;

    }

    private CaseData generateNewCaseDataCaseTransfer(CaseData oldCaseData, String caseId,
                                                  String ccdGatewayBaseUrl, String positionTypeCT) {

        return copyCaseData(oldCaseData, new CaseData(), caseId, ccdGatewayBaseUrl, positionTypeCT);

    }

    private CaseData generateCaseDataCaseTransfer(CaseData newCaseData, CaseData oldCaseData, String caseId,
                                                     String ccdGatewayBaseUrl, String positionTypeCT) {

        return copyCaseData(oldCaseData, newCaseData, caseId, ccdGatewayBaseUrl, positionTypeCT);

    }

    private CaseData copyCaseData(CaseData oldCaseData, CaseData newCaseData, String caseId,
                                  String ccdGatewayBaseUrl, String positionTypeCT) {
        newCaseData.setEthosCaseReference(oldCaseData.getEthosCaseReference());
        newCaseData.setPositionType(positionTypeCT);
        newCaseData.setCaseType(oldCaseData.getCaseType());
        newCaseData.setClaimantTypeOfClaimant(oldCaseData.getClaimantTypeOfClaimant());
        newCaseData.setClaimantCompany(oldCaseData.getClaimantCompany());
        newCaseData.setClaimantIndType(oldCaseData.getClaimantIndType());
        newCaseData.setClaimantType(oldCaseData.getClaimantType());
        newCaseData.setClaimantOtherType(oldCaseData.getClaimantOtherType());
        newCaseData.setPreAcceptCase(oldCaseData.getPreAcceptCase());
        newCaseData.setReceiptDate(oldCaseData.getReceiptDate());
        newCaseData.setFeeGroupReference(oldCaseData.getFeeGroupReference());
        newCaseData.setClaimantWorkAddressQuestion(oldCaseData.getClaimantWorkAddressQuestion());
        newCaseData.setClaimantWorkAddressQRespondent(oldCaseData.getClaimantWorkAddressQRespondent());
        newCaseData.setRepresentativeClaimantType(oldCaseData.getRepresentativeClaimantType());
        newCaseData.setRespondentCollection(oldCaseData.getRespondentCollection());
        newCaseData.setRepCollection(oldCaseData.getRepCollection());
        newCaseData.setPositionType(oldCaseData.getPositionTypeCT());
        newCaseData.setDateToPosition(oldCaseData.getDateToPosition());
        newCaseData.setCurrentPosition(oldCaseData.getCurrentPosition());
        newCaseData.setDepositCollection(oldCaseData.getDepositCollection());
        newCaseData.setJudgementCollection(oldCaseData.getJudgementCollection());
        newCaseData.setJurCodesCollection(oldCaseData.getJurCodesCollection());
        newCaseData.setBfActions(oldCaseData.getBfActions());
        newCaseData.setUserLocation(oldCaseData.getUserLocation());
        newCaseData.setDocumentCollection(oldCaseData.getDocumentCollection());
        newCaseData.setAdditionalCaseInfoType(oldCaseData.getAdditionalCaseInfoType());
        newCaseData.setCaseNotes(oldCaseData.getCaseNotes());
        newCaseData.setClaimantWorkAddress(oldCaseData.getClaimantWorkAddress());
        newCaseData.setClaimantRepresentedQuestion(oldCaseData.getClaimantRepresentedQuestion());
        newCaseData.setCaseSource(oldCaseData.getCaseSource());
        newCaseData.setEt3Received(oldCaseData.getEt3Received());
        newCaseData.setConciliationTrack(oldCaseData.getConciliationTrack());
        newCaseData.setCounterClaim(oldCaseData.getCounterClaim());
        newCaseData.setRestrictedReporting(oldCaseData.getRestrictedReporting());
        newCaseData.setRespondent(oldCaseData.getRespondent());
        newCaseData.setClaimant(oldCaseData.getClaimant());
        newCaseData.setCaseRefECC(oldCaseData.getCaseRefECC());
        newCaseData.setCcdID(oldCaseData.getCcdID());
        newCaseData.setFlagsImageAltText(oldCaseData.getFlagsImageAltText());
        newCaseData.setCompanyPremises(oldCaseData.getCompanyPremises());

        newCaseData.setLinkedCaseCT(generateMarkUp(ccdGatewayBaseUrl, caseId, oldCaseData.getEthosCaseReference()));
        return newCaseData;
    }

    private String generateMarkUp(String ccdGatewayBaseUrl, String caseId, String ethosCaseRef) {

        String url = ccdGatewayBaseUrl + "/cases/case-details/" + caseId;

        return "<a target=\"_blank\" href=\"" + url + "\">" + ethosCaseRef + "</a>";

    }

}
