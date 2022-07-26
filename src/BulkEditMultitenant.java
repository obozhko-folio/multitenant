import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class BulkEditMultitenant {

    public static String TENANT_INVOICE = "invoice_api_tests";
    public static String TOKEN_INVOICE = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbi11c2VyIiwidHlwZSI6ImxlZ2FjeS1hY2Nlc3MiLCJ1c2VyX2lkIjoiMDAwMDAwMDAtMTExMS01NTU1LTk5OTktOTk5OTk5OTk5OTk5IiwiaWF0IjoxNjU4NjU0NTU4LCJ0ZW5hbnQiOiJpbnZvaWNlX2FwaV90ZXN0cyJ9.Y3YFNBT5lYnExZWUvcnuaR2UcZLUGJoh625zyP3mleU";
    public static String TENANT_DIKU = "diku";
    public static String TOKEN_DIKU = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidHlwZSI6ImxlZ2FjeS1hY2Nlc3MiLCJ1c2VyX2lkIjoiODQzYWMwMGItMDNjZi01YTY2LWE2NDgtMTczOGE1ODc5NmI1IiwiaWF0IjoxNjU4NjU0OTUzLCJ0ZW5hbnQiOiJkaWt1In0.9aRxzV9xni576fdrYBqPhYY0Ri3zDUUVnRU6v1FONow";
    public static String URL = "http://localhost:9130";
    public static String INPUT_FILE_DIKU = "items_barcodes_13.csv";
    public static String INPUT_FILE_INVOICE = "barcode_inv_api_test_13.csv";

    public static void main (String[] args) throws InterruptedException {
        var jtaToken = new JTextArea(5, 40);
        jtaToken.setText(TOKEN_DIKU);
        var jtfInputFile = new JTextField(INPUT_FILE_DIKU);
        var comboBoxTenant = new JComboBox<>(new String[]{TENANT_DIKU, TENANT_INVOICE});
        comboBoxTenant.addActionListener(e -> {
            if (comboBoxTenant.getSelectedItem().equals(TENANT_DIKU)) {
                jtaToken.setText(TOKEN_DIKU);
                jtfInputFile.setText(INPUT_FILE_DIKU);
            } else {
                jtaToken.setText(TOKEN_INVOICE);
                jtfInputFile.setText(INPUT_FILE_INVOICE);
            }
        });
        var jtfHost = new JTextField(URL);
        jtaToken.setLineWrap(true);
        var panel = Box.createVerticalBox();
        panel.add(new JLabel("Enter token:"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(jtaToken);
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("Select tenant:"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(comboBoxTenant);
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("Enter host:"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(jtfHost);
        panel.add(new JLabel("Enter input file:"));
        panel.add(Box.createVerticalStrut(5));
        panel.add(jtfInputFile);
        if (JOptionPane.showConfirmDialog(null, panel) == JOptionPane.OK_OPTION) {
            var tenant = (String)comboBoxTenant.getSelectedItem();
            var token = jtaToken.getText().trim();
            var host = jtfHost.getText().trim();
            var inputFilename = jtfInputFile.getText().trim();
            for (int i = 0; i < 10_000; i ++) {
                var jobId = postJob(host, token, tenant);
                Thread.sleep(1000);
                uploadFile(jobId, host, token, tenant, inputFilename);
                startJob(jobId, host, token, tenant);
                Thread.sleep(800);
            }
        }
    }

    private static String postJob(String host, String token, String tenant) {
        HttpPost request = new HttpPost(host + "/data-export-spring/jobs");
        request.setHeader("x-okapi-token", token);
        request.setHeader("x-okapi-tenant", tenant);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            request.setEntity(new StringEntity("{\n" +
                    "    \"type\": \"BULK_EDIT_IDENTIFIERS\",\n" +
                    "    \"entityType\": \"ITEM\",\n" +
                    "    \"exportTypeSpecificParameters\": {},\n" +
                    "    \"identifierType\": \"BARCODE\"\n" +
                    "}"));
            HttpResponse response = client.execute(request);
            String content = new String(response.getEntity().getContent().readAllBytes());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                throw new RuntimeException(tenant + " Failed to post job: " + response.getStatusLine().getStatusCode() + "\n" + content);
            } else {
                System.out.println(tenant + " Successfully created job: " + content);
                return new JSONObject(content).getString("id");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void uploadFile(String jobId, String host, String token, String tenant, String inputFilename) {
        HttpPost request = new HttpPost(host + "/bulk-edit/" + jobId + "/upload");
        request.setHeader("x-okapi-token", token);
        request.setHeader("x-okapi-tenant", tenant);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            File file = new File(inputFilename);
            request.setEntity(MultipartEntityBuilder.create()
                    .addBinaryBody("file", file, ContentType.create("text/csv"), file.getName())
                    .build());
            HttpResponse response = client.execute(request);
            String content = new String(response.getEntity().getContent().readAllBytes());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new RuntimeException(tenant + " Failed to upload file: " + response.getStatusLine().getStatusCode() + "\n" + content);
            } else {
                System.out.println(tenant + " Successfully uploaded file: " + content);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void startJob(String jobId, String host, String token, String tenant) {
        HttpPost request = new HttpPost(host + "/bulk-edit/" + jobId + "/start");
        request.setHeader("x-okapi-token", token);
        request.setHeader("x-okapi-tenant", tenant);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpResponse response = client.execute(request);
            String content = new String(response.getEntity().getContent().readAllBytes());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new RuntimeException(tenant + " Failed to start job: " + response.getStatusLine().getStatusCode() + "\n" + content);
            } else {
                System.out.println(tenant + " Successfully started job");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
