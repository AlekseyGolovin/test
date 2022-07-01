// Импортируем классы для удобного обращения к ним
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
//import java.sql.Date; // есть в java.util.*

import java.util.Properties;
import java.util.Date; // есть в java.sql.*

import java.net.URL;
import java.net.HttpURLConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class Main {
    public static void main(String[] args) throws Exception {

        // Настройки Базы данных
        String dbLocation = "localhost/3050:C:\\ProgramData\\UkrSklad6S\\db\\Sklad.tcb";

        // Настройки отправки почты
        String mailTo = "**********@***";
        String mailFrom = "**********@***";
        String mailFromPerson = "Aleksey Golovin";
        String mailPassword = "***************";
        String mailSmtpHost = "smtp.host.net";
        String mailSmtpPort = "465";

        // Настройки CRM
        String crmApiKey = "*************************";

        // Тело для письма если отсутствуют штрихкоды
        String mailText = "";

        // Фиксируем дату начала
        Date dateStart = new Date();

        System.out.println("Work started!");

        // Указываем параметры подключения
        Properties dbProperties = new Properties();
        dbProperties.setProperty("user", "SYSDBA");
        dbProperties.setProperty("password", "masterkey");
        dbProperties.setProperty("encoding", "WIN1251");

        // DriverManager загружает драйверы из системного пути к классам.
        Class.forName("org.firebirdsql.jdbc.FBDriver");

        // Создаем подключение исходя из наших настроек
        Connection dbConnection = DriverManager.getConnection( "jdbc:firebirdsql:"+dbLocation, dbProperties);

        String sqlString;
        sqlString = "SELECT "+
                        //"FIRST 700 "+
                        "NUM, "+
                        "TOV_SCANCODE, "+
                        "(SELECT KOLVO FROM TOVAR_ZAL TZ WHERE TZ.TOVAR_ID=TN.NUM AND SKLAD_ID=2), "+
                        "NAME, "+
                        "KOD "+
                    "FROM "+
                        "TOVAR_NAME TN " +
                    "WHERE " +
                        "VISIBLE=1 " +
                    "ORDER BY " +
                        "NUM";


        try {


            // Создаем канал в подключении (запрос) и получаем ответ на запрос
            Statement stmtSelect = dbConnection.createStatement();

            ResultSet rs = stmtSelect.executeQuery(sqlString);

            // Ответ получен
            int i = 0;
            while (rs.next()) {

                // Шагаем пока есть данные
                if (rs.getString(3) != null) {

                    // Количество не пустое
                    i++;
                    int productId = rs.getInt(1);
                    String productBarCode = rs.getString(2);
                    int productQnt = rs.getInt(3);
                    String productName = rs.getString(4);
                    String productCode = rs.getString(5);

                    // Выводим инфо о товаре
                    System.out.print("["+i+"] \t["+productId+"] \t"+productBarCode+" \t"+productQnt+" шт. \t");

                    // Проверяем наличие штрихкода
                    if (rs.getString(2) == null){
                        // штрихкод отсутствует
                        //System.out.println("Укажите штрихкод для товара ["+productCode+"] "+productName);
                        System.out.println();

                        mailText += "<tr><td> &nbsp; "+productCode+" &nbsp; </td><td> &nbsp; "+productName+" &nbsp; </td></tr>";
                    }
                    else {
                        // штрихкод есть

                        // Шлем запрос в СРМ
                        String url = "http://crm.com/api/v1/search?key="+crmApiKey+"&barcode="+productBarCode;

                        URL urlobj = new URL(url);
                        HttpURLConnection httpConnection = (HttpURLConnection) urlobj.openConnection();

                        httpConnection.setRequestMethod("GET");

                        BufferedReader in = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        //System.out.println(response.toString());

                        Object obj = JSONValue.parse(response.toString());
                        JSONObject jo = (JSONObject) obj;

                        // Получаем значение параметра по наименованию result
                        String result = jo.get("result").toString();
                        //System.out.println("result: " + result);

                        // Так как параметр всегда разный получаем наименование первого параметра
                        String[] parts = result.split(":", 2);
                        String part = parts[0];
                        part = part.substring(2);
                        part = part.substring(0,part.length()-1);
                        //System.out.println(part);

                        // Получаем значение этого ключа
                        obj = JSONValue.parse(result);
                        jo = (JSONObject) obj;
                        result = jo.get(part).toString();
                        //System.out.println("result: " + result);

                        obj = JSONValue.parse(result);
                        jo = (JSONObject) obj;
                        result = jo.get("amount").toString();
                        //System.out.println("amount: " + result);

                        int crmQnt = strToInt(result);
                        System.out.println("CRM " + crmQnt+" шт.");


                        if (productQnt != crmQnt) {
                            // Количество отличается
                            // Обновляем информацию в УС
                            sqlString = "UPDATE TOVAR_ZAL SET KOLVO="+crmQnt+" WHERE TOVAR_ID="+productId+" AND SKLAD_ID=2";
                            System.out.println("SQL: " + sqlString);

                            Connection dbUpdConnection = DriverManager.getConnection( "jdbc:firebirdsql:"+dbLocation, dbProperties);

                            try {
                                Statement stmtUpdate = dbUpdConnection.createStatement();
                                stmtUpdate.executeUpdate(sqlString);

                            }
                            catch(Exception ex){
                                System.out.println("Connection failed...");

                                System.out.println(ex);
                            }


                        }
                    }
                }
            }
            if (mailText.length()>0){
                // Есть о чем рассказать
                // Отправляем почту

                mailText = "<table>" + mailText + "</table>";

                Properties mailProperties = new Properties();
                mailProperties.setProperty("mail.transport.protocol", "smtp");
                mailProperties.setProperty("mail.smtp.host", mailSmtpHost);
                mailProperties.setProperty("mail.smtp.user", mailFrom);
                mailProperties.setProperty("mail.password", mailPassword);
                mailProperties.setProperty("mail.smtp.port", mailSmtpPort);
                mailProperties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");
                mailProperties.setProperty("mail.smtp.starttls.enable", "true");
                mailProperties.setProperty("mail.smtp.ssl.enable", "true");
                mailProperties.setProperty("mail.smtp.auth", "true");
                //mailProperties.setProperty("mail.debug", "true");

                Authenticator mailAuthenticator = new Authenticator() {
                     protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(mailFrom, mailPassword);
                    }
                };

                Session mailSession = Session.getInstance(mailProperties, mailAuthenticator);

                System.out.println("Mail session created");

                try {
                    MimeMessage mailMessage = new MimeMessage(mailSession); // email message

                    mailMessage.addHeader("Content-type", "text/HTML; charset=UTF-8");
                    mailMessage.addHeader("format", "flowed");
                    mailMessage.addHeader("Content-Transfer-Encoding", "8bit");
                    mailMessage.setFrom(new InternetAddress(mailFrom, mailFromPerson)); // setting header fields
                    mailMessage.setSubject("Товары без штрихкодов", "UTF-8"); // subject line
                    mailMessage.setContent(mailText, "text/html;charset=utf-8");
                    mailMessage.setSentDate(new java.util.Date());
                    mailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(mailTo, false));

                    System.out.println("Message is ready");

                    // Send message
                    Transport.send(mailMessage);

                    System.out.println("Email Sent successfully....");

                } catch (MessagingException mex){ mex.printStackTrace(); }
            }
        }
        catch(Exception ex){
            System.out.println("Connection failed...");

            System.out.println(ex);
        }


        // Фиксируем дату Окончания
        Date dateStop = new Date();



        System.out.println("Начало   : "+dateStart);
        System.out.println("Окончание: "+dateStop);

    }


    //********************************************************************************

    // Функция преобразования строки в число убирая мусор
    public static int strToInt(String strNum)
    {
        String result = "";
        char ch;
        for (int i = 0; i < strNum.length(); i++) {
            ch = strNum.charAt(i);

            if (ch>='0' && ch<='9') { result += ch; }
        }

        if (result.length() == 0) result = "0";

        return Integer.parseInt (result.trim ());
    }


}


