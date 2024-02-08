package sk.drabikp.bzscraper.bandsintown;

import java.time.Duration;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import com.bastiaanjansen.otp.HMACAlgorithm;
import com.bastiaanjansen.otp.TOTPGenerator;

public class BITTest {
    public static void main(String[] args) throws InterruptedException {
        System.setProperty("webdriver.gecko.driver", "geckodriver");
        WebDriver driver = new FirefoxDriver();

        driver.get("https://www.artist.bandsintown.com/");
        List<WebElement> buttons = driver.findElements(By.className("TFOeq0"));

        if (CollectionUtils.isEmpty(buttons)) {
            return;
        }

        if (buttons.size() > 1) {
            buttons.get(1).click();
        }

        //driver.wait(2000L);
        Thread.sleep(2000L);

        driver.findElement(By.id("loginEmail")).sendKeys("drabik.p@gmail.com");
        driver.findElement(By.id("loginPassword")).sendKeys("p25BvZ112!");
        driver.findElements(By.className("fjzCZx")).get(0).click();

        TOTPGenerator totp = new TOTPGenerator.Builder("SAVJAH3V7IH5URQUBXN4CMJMLVF72LCZCYJOJNS5VTO73XJ6WEBQ".getBytes())
                .withHOTPGenerator(builder -> {
                    builder.withPasswordLength(6);
                    builder.withAlgorithm(HMACAlgorithm.SHA1);
                })
                .withPeriod(Duration.ofSeconds(30))
                .build();

        String code = totp.now();
        Thread.sleep(2000L);
        List<WebElement> otpCodeInputs = driver.findElement(By.className("hReupH")).findElements(By.tagName("input"));
        //List<WebElement> otpCodeInputs = driver.findElement(By.className("hReupH")).findElements(By.tagName("gArcEt"));

        //TODO hardening

        for (int i = 0; i < otpCodeInputs.size(); i++) {
            otpCodeInputs.get(i).sendKeys(String.valueOf(code.charAt(i)));
            Thread.sleep(1000L);
        }

        //driver.findElement(By.className("gTbYOx")).click();
        driver.findElement(By.className("continue")).click();

    }
}
