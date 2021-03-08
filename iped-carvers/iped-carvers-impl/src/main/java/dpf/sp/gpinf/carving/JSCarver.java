package dpf.sp.gpinf.carving;

import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.carver.api.InvalidCarvedObjectException;
import iped3.IItem;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.security.cert.Certificate;

public class JSCarver extends DefaultCarver {
    ScriptEngine engine;
    Invocable inv;

    // inicializa um default carver com script
    public JSCarver(File scriptFile)
            throws UnsupportedEncodingException, FileNotFoundException, IOException, ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        String ext = scriptFile.getName().substring(scriptFile.getName().lastIndexOf('.') + 1); // $NON-NLS-1$
        this.engine = manager.getEngineByExtension(ext); // $NON-NLS-1$
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(scriptFile), "UTF-8")) {
            engine.eval(reader);
        }
        this.inv = (Invocable) engine;
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        try {
            Double l = (Double) inv.invokeFunction("getLengthFromHeader", parentEvidence, header);
            return l.longValue();
        } catch (ScriptException e) {
            throw new IOException(e);
        } catch (NoSuchMethodException e) {
            return super.getDefaultLengthFromHeader(parentEvidence, header); // se o método não for implementado assume
            // o comportamento padrão
        } catch (Exception e) {
            // caso uma exceção ocorra dentro do script
            throw new IOException(e);
        }
    }

    @Override
    public Object validateCarvedObject(IItem parentEvidence, Hit header, long length)
            throws InvalidCarvedObjectException {
        try {
            Certificate cert = (Certificate) inv.invokeFunction("validateCarvedObject", parentEvidence, header, length);
            return cert;
        } catch (ScriptException e) {
            throw new InvalidCarvedObjectException(e);
        } catch (NoSuchMethodException e) {
            return super.validateCarvedObject(parentEvidence, header, length); // se o método de validação não for
            // implementado considera o objeto
            // válido
        }
    }

    @Override
    public IItem carveFromHeader(IItem parentEvidence, Hit header) throws IOException {
        try {
            IItem e = (IItem) inv.invokeFunction("carveFromHeader", parentEvidence, header);
            return e;
        } catch (ScriptException e) {
            throw new IOException(e);
        } catch (NoSuchMethodException e) {
            return super.carveFromHeader(parentEvidence, header); // se o método de validação não for implementado
            // considera o objeto válido
        }
    }

    @Override
    public IItem carveFromFooter(IItem parentEvidence, Hit footer) throws IOException {
        try {
            IItem e = (IItem) inv.invokeFunction("carveFromFooter", parentEvidence, footer);
            return e;
        } catch (ScriptException e) {
            throw new IOException(e);
        } catch (NoSuchMethodException e) {
            return super.carveFromFooter(parentEvidence, footer);
        }
    }

    @Override
    public CarverType[] getCarverTypes() {
        try {
            CarverType[] cts = (CarverType[]) inv.invokeFunction("getCarverTypes");
            return cts;
        } catch (ScriptException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return super.getCarverTypes();
        }
    }

    @Override
    public void notifyHit(IItem parentEvidence, Hit hit) throws IOException {
        try {
            inv.invokeFunction("notifyHit", parentEvidence, hit);
        } catch (ScriptException e) {
            throw new IOException(e);
        } catch (NoSuchMethodException e) {
            super.notifyHit(parentEvidence, hit);
        }
    }

    @Override
    public void notifyEnd(IItem parentEvidence) throws IOException {
        try {
            inv.invokeFunction("notifyEnd", parentEvidence);
        } catch (ScriptException e) {
            throw new IOException(e);
        } catch (NoSuchMethodException e) {
            super.notifyEnd(parentEvidence);
        }
    }
}
