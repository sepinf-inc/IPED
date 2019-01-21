package br.gov.pf.labld.iped.regex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import dpf.sp.gpinf.indexer.process.task.regex.RegexValidatorService;

public class TelefoneRegexValidatorServiceTest {

  private RegexValidatorService service = new TelefoneRegexValidatorService();

  @Test
  public void testFormatWhatsapp1() {
    String ipl = "5511998765555@s.whatsapp.net";
    assertEquals("+55 11 99876-5555", service.format(ipl));
  }

  @Test
  public void testFormatCallMessageMetadata() {
    String ipl = "Message-To: Maicon(011999445555)  ";
    assertEquals("+55 11 99944-5555", service.format(ipl));
  }

  @Test
  public void testFormatCallMessageMetadataSimple() {
    String ipl = "Message-To: 011999445555  ";
    assertEquals("+55 11 99944-5555", service.format(ipl));
  }

  @Test
  public void testFormatWhatsapp2() {
    String ipl = "WhatsApp Chat - 5511998765555";
    assertEquals("+55 11 99876-5555", service.format(ipl));
  }

  @Test
  public void testFormatWhatsapp2Simple() {
    String ipl = "WhatsApp Chat - Fulano - 5511998765555";
    assertEquals("+55 11 99876-5555", service.format(ipl));
  }

}
