package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ProfileRepository profileRepository;
    private final EmailService emailService;
    private final ExpenceService expenceService;

    @Value("${money.manager.frontend.url}")
    private String frontendUrl;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Kolkata")
    public void sendDailyIncomeExpenceReminder(){
        log.info("Sending daily income and expence reminder: sendDailyIncomeExpenceReminder ");
        List<ProfileEntity> profiles = profileRepository.findAll();
        if(profiles.isEmpty()) {
            log.warn("No profiles found in database. Skipping email sending.");
            return;
        }
        for(ProfileEntity profile : profiles){
            try {
            String body =
                    "<div style='font-family: Arial, sans-serif; background-color: #f4f6f8; padding: 20px;'>"
                            + "  <div style='max-width: 500px; margin: auto; background-color: #ffffff; "
                            + "      padding: 25px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05);'>"
                            + "      <h2 style='color: #2c3e50; margin-bottom: 10px;'>Daily Reminder 📊</h2>"
                            + "      <p style='color: #555; font-size: 15px;'>"
                            + "          Hi <strong>" + profile.getFullname() + "</strong>,"
                            + "      </p>"
                            + "      <p style='color: #555; font-size: 15px; line-height: 1.6;'>"
                            + "          Don’t forget to update your <strong>income</strong> and "
                            + "          <strong>expenses</strong> for today."
                            + "      </p>"
                            + "      <p style='color: #555; font-size: 15px;'>"
                            + "          Staying consistent helps you track your financial growth."
                            + "      </p>"
                            + "      <div style='text-align: center; margin-top: 20px;'>"
                            + "          <a href='" + frontendUrl + "' "
                            + "             style='background-color: #2c3e50; color: #ffffff; "
                            + "                    padding: 10px 18px; text-decoration: none; "
                            + "                    border-radius: 5px; font-size: 14px;'>"
                            + "              Open Money Manager"
                            + "          </a>"
                            + "      </div>"
                            + "      <hr style='margin: 25px 0; border: none; border-top: 1px solid #eee;'>"
                            + "      <p style='font-size: 12px; color: #999; text-align: center;'>"
                            + "          This is an automated reminder from Money Manager."
                            + "      </p>"
                            + "  </div>"
                            + "</div>";

            emailService.sendEmail(profile.getEmail(), "Daily Reminder: Update Your Income and Expenses", body);
            log.info("Daily income and expence reminder sent to: {}", profile.getEmail());
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", profile.getEmail(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Kolkata")
    public void sendDailyExpenceSummery(){
        log.info("Sending daily expence summery: sendDailyExpenceSummery ");

        List<ProfileEntity> profiles = profileRepository.findAll();
        for(ProfileEntity profile : profiles) {
            List<ExpenceDTO> todaysexpences = expenceService.getExpenceForUserOnDate(profile.getId(), LocalDate.now(ZoneId.of("Asia/Kolkata")));
            if(!todaysexpences.isEmpty()) {
                StringBuilder table = new StringBuilder();
                table.append("<table border='1' cellpadding='5' cellspacing='0' style='width: 100%; border-collapse: collapse; font-family: Arial, sans-serif;'>");
                table.append("<tr style='background-color: #f2f2f2;'><th style='border: 1px solid #ddd; padding: 8px;'>S.No</th><th style='border: 1px solid #ddd; padding: 8px;'>Name</th><th style='border: 1px solid #ddd; padding: 8px;'>Amount</th><th style='border: 1px solid #ddd; padding: 8px;'>Category</th></tr>");
                int i = 1;
               for(ExpenceDTO expence : todaysexpences) {
                   table.append("<tr>");
                   table.append("<td style='border: 1px solid #ddd; padding: 8px;'>").append(i++).append("</td>");
                   table.append("<td style='border: 1px solid #ddd; padding: 8px;'>").append(expence.getName()).append("</td>");
                   table.append("<td style='border: 1px solid #ddd; padding: 8px;'>").append(expence.getAmount()).append("</td>");
                   table.append("<td style='border: 1px solid #ddd; padding: 8px;'>").append(expence.getCategoryId() != null ? expence.getCategoryName() : "N/A").append("</td>");
                   table.append("</tr>");
               }
               table.append("</table>");

                String body =
                        "<div style='font-family: Arial, sans-serif; background-color: #f4f6f8; padding: 20px;'>"
                                + "  <div style='max-width: 600px; margin: auto; background-color: #ffffff; "
                                + "      padding: 25px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05);'>"
                                + "      <h2 style='color: #2c3e50; margin-bottom: 10px;'>Your Daily Expense Summary 📊</h2>"
                                + "      <p style='color: #555; font-size: 15px;'>"
                                + "          Hi <strong>" + profile.getFullname() + "</strong>,"
                                + "      </p>"
                                + "      <p style='color: #555; font-size: 15px; line-height: 1.6;'>"
                                + "          Here’s a summary of your expenses for today:"
                                + "      </p>"
                                + table.toString()
                                + "      <div style='text-align: center; margin-top: 20px;'>"
                                + "          <a href='" + frontendUrl + "' "
                                + "             style='background-color: #2c3e50; color: #ffffff; "
                                + "                    padding: 10px 18px; text-decoration: none; "
                                + "                    border-radius: 5px; font-size: 14px;'>"
                                + "              View in Money Manager"
                                + "          </a>"
                                + "      </div>"
                                + "      <hr style='margin: 25px 0; border: none; border-top: 1px solid #eee;'>"
                                + "      <p style='font-size: 12px; color: #999; text-align: center;'>"
                                + "          This is an automated summary from Money Manager."
                                + "      </p>"
                                + "  </div>"
                                + "</div>";

                emailService.sendEmail(profile.getEmail(), "Your Daily Expense Summary", body);
            }
        }
        log.info("Daily expence summery sent to");
    }
}
