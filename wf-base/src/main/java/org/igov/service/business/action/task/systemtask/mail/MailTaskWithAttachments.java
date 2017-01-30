package org.igov.service.business.action.task.systemtask.mail;

import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.task.Attachment;
import org.apache.commons.mail.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.igov.io.mail.Mail;

import javax.activation.DataSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.json.simple.JSONArray;

import static org.igov.service.business.action.task.core.AbstractModelTask.getStringFromFieldExpression;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author BW
 */
@Component("MailTaskWithAttachments")
public class MailTaskWithAttachments extends Abstract_MailTaskCustom {

    private final static Logger LOG = LoggerFactory.getLogger(MailTaskWithAttachments.class);

    private Expression saAttachmentsForSend;

    @Override
    public void execute(DelegateExecution oExecution) throws Exception {
        LOG.info("MailTaskWithAttachments start...");
        System.setProperty("mail.mime.address.strict", "false");

        //MultiPartEmail oMultiPartEmail = MultiPartEmail_BaseFromTask(oExecution);
        Mail oMail = Mail_BaseFromTask(oExecution);

        String sAttachmentsForSend = getStringFromFieldExpression(this.saAttachmentsForSend, oExecution);
        LOG.info("sAttachmentsForSend is: " + sAttachmentsForSend);
        /*sAttachmentsForSend = sAttachmentsForSend == null ? "" : sAttachmentsForSend;
        LOG.info("(sAttachmentsForSend={})", sAttachmentsForSend);
        List<Attachment> aAttachment = new ArrayList<>();
        String[] asID_Attachment = sAttachmentsForSend.split(",");
        for (String sID_Attachment : asID_Attachment) {
            //log.info("sID_Attachment=" + sID_Attachment);
            if (sID_Attachment != null && !"".equals(sID_Attachment.trim()) && !"null".equals(sID_Attachment.trim())) {
                String sID_AttachmentTrimmed = sID_Attachment.replaceAll("^\"|\"$", "");
                LOG.info("(sID_AttachmentTrimmed={})", sID_AttachmentTrimmed);
                Attachment oAttachment = taskService.getAttachment(sID_AttachmentTrimmed);
                if (oAttachment != null) {
                    aAttachment.add(oAttachment);
                }
            } else {
                LOG.warn("(sID_Attachment={})", sID_Attachment);
            }
        }*/
        List<Attachment> aAttachment = findAttachments(sAttachmentsForSend, oExecution.getId());
        if (!aAttachment.isEmpty()) {
            InputStream oInputStream_Attachment = null;
            String sFileName;
            String sFileExt;
            String sDescription;
            for (Attachment oAttachment : aAttachment) {
                sFileName = oAttachment.getName();
                String sExt="";
                int nAt=sFileName.lastIndexOf(".");
                if(nAt>=0){
                    sExt = sFileName.substring(nAt);
                }

                if (sFileName != null && !sFileName.toLowerCase().endsWith(".xml") && !sFileName.toLowerCase().endsWith(".rpl")) {
                    sFileName = "Attach_"+oAttachment.getId()+sExt; //
                }
                
                sFileExt = oAttachment.getType().split(";")[0];
                sDescription = oAttachment.getDescription();
                if (sDescription == null || "".equals(sDescription.trim())) {
                    sDescription = "(no description)";
                }
                LOG.info("(oAttachment.getId()={}, sFileName={}, sFileExt={}, sDescription={})",
                        oAttachment.getId(), sFileName, sFileExt, sDescription);
                oInputStream_Attachment = oExecution.getEngineServices().getTaskService()
                        .getAttachmentContent(oAttachment.getId());
                if (oInputStream_Attachment == null) {
                    LOG.error("Attachment with (id={}) doesn't have content associated with it.", oAttachment.getId());
                    throw new ActivitiObjectNotFoundException(
                            "Attachment with id '" + oAttachment.getId() + "' doesn't have content associated with it.",
                            Attachment.class);
                }
                DataSource oDataSource = new ByteArrayDataSource(oInputStream_Attachment, sFileExt);
                oMail._Attach(oDataSource, sFileName, sDescription);
                LOG.info("oMultiPartEmail.attach: Ok!");
            }
        } 
        else {
            
            JSONObject oJsonTaskAttachVO = null;
            JSONParser parser = new JSONParser(); 
            
            List<String> asJsonAttachements = Arrays.asList(sAttachmentsForSend.split(","));
           
            for(String sJsonAttach : asJsonAttachements)
            {
               try {
                    oJsonTaskAttachVO = (JSONObject)parser.parse(sJsonAttach);
                } catch (ParseException ex) {
                    LOG.info("There aren't TaskAttachVO objects in mail - JSON parsing error: ", ex);
                }

                MultipartFile oMultipartFile = null;

                if(oJsonTaskAttachVO != null && oJsonTaskAttachVO.get("sID_StorageType") != null)
                {
                    String sFileExt = (String)oJsonTaskAttachVO.get("sFileNameAndExt");
                    oMultipartFile = oAttachmetService
                        .getAttachment(oExecution.getProcessInstanceId(), (String) oJsonTaskAttachVO.get("sFieldID"), null, null);

                    InputStream oInputStream_Attachment = oMultipartFile.getInputStream();

                    DataSource oDataSource = new ByteArrayDataSource(oInputStream_Attachment, sFileExt);
                    oMail._Attach(oDataSource, sFileExt, "");
                    LOG.info("oMultiPartEmail.attach was sending by new schema!");

                }
                else
                {
                    LOG.error("aAttachment has nothing!");
                    throw new ActivitiObjectNotFoundException("add the file to send");
                }
            }
        }

        oMail.send();
    }

}
