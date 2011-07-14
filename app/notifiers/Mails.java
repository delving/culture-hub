package notifiers;

import models.User;
import play.mvc.Mailer;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class Mails extends Mailer {

    public static void activation(User user) {
        setSubject("Welcome to CultureHub");
        addRecipient(user.email());
        ThemeAwareBridge.before();
        try {
            setFrom(ThemeAwareBridge.theme().emailTarget().systemFrom());
        } finally {
            ThemeAwareBridge.after();
        }
        send(user);
    }

}
