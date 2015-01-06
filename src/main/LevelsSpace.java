
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.MenuElement;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.nlogo.api.*;
import org.nlogo.app.App;
import org.nlogo.api.ExtensionObject;
import org.nlogo.api.ImportErrorHandler;
import org.nlogo.api.LogoException;
import org.nlogo.api.LogoList;
import org.nlogo.api.LogoListBuilder;
import org.nlogo.api.PrimitiveManager;
import org.nlogo.api.Syntax;
import org.nlogo.app.ToolsMenu;
import org.nlogo.nvm.CommandTask;
import org.nlogo.nvm.ExtensionContext;
import org.nlogo.nvm.HaltException;
import org.nlogo.nvm.ReporterTask;
import org.nlogo.window.SpeedSliderPanel;
import org.nlogo.window.ViewUpdatePanel;


public class LevelsSpace implements org.nlogo.api.ClassManager {

    final static HashMap<Integer, ChildModel> myModels = new HashMap<Integer, ChildModel>();

    // counter for keeping track of new models
    static int modelCounter = 0;

    @Override
    public void load(PrimitiveManager primitiveManager) throws ExtensionException {
        primitiveManager.addPrimitive("ask", new Ask());
        primitiveManager.addPrimitive("of", new Of());
        primitiveManager.addPrimitive("load-headless-model", new LoadHeadlessModel());
        primitiveManager.addPrimitive("load-gui-model", new LoadGUIModel());
        primitiveManager.addPrimitive("model-name", new ModelName());
        primitiveManager.addPrimitive("close-model", new CloseModel());
        primitiveManager.addPrimitive("models", new AllModels());
        primitiveManager.addPrimitive("model-exists?", new ModelExists());
        primitiveManager.addPrimitive("reset", new Reset());
        primitiveManager.addPrimitive("last-model-id", new LastModel());
        primitiveManager.addPrimitive("model-path", new ModelPath());
        primitiveManager.addPrimitive("display", new UpdateView());
        primitiveManager.addPrimitive("show", new Show());
        primitiveManager.addPrimitive("hide", new Hide());
        primitiveManager.addPrimitive("_list-breeds", new ListBreeds());
        primitiveManager.addPrimitive("_globals", new Globals());
        primitiveManager.addPrimitive("_breeds-own", new BreedsOwns());
        primitiveManager.addPrimitive("ask-descendant", new HierarchicalAsk());
        primitiveManager.addPrimitive("of-descendant", new HierarchicalOf());
        primitiveManager.addPrimitive("uses-level-space?", new UsesLevelSpace());

        if (useGUI()) {
            // Adding event listener to Halt for halting child models
            MenuElement[] elements = App.app().frame().getJMenuBar().getSubElements();
            for (MenuElement e : elements) {
                if (e instanceof ToolsMenu) {
                    ToolsMenu tm = (ToolsMenu) e;
                    JMenuItem item = tm.getItem(0);
                    item.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            haltChildModels(myModels);
                        }
                    });
                }
            }

            // Attaching a ChangeEventLister to the main model's speed slider so we can
            // update child models' speed sliders at the same time.
            Component[] c = App.app().tabs().interfaceTab().getComponents();
            for (Component co : c) {
                Component[] c2 = ((Container) co).getComponents();
                for (Component co2 : c2) {
                    if (co2 instanceof ViewUpdatePanel) {
                        Component[] c3 = ((Container) co2).getComponents();
                        for (Component co3 : c3) {
                            if (co3 instanceof SpeedSliderPanel) {
                                SpeedSliderPanel speedSliderPanel = (SpeedSliderPanel) co3;
                                JSlider slider = (JSlider) speedSliderPanel.getComponents()[0];
                                slider.addChangeListener(new ChangeListener() {
                                    @Override
                                    public void stateChanged(ChangeEvent arg0) {
                                        updateChildModelsSpeed();
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean useGUI() {
        return !"true".equals(System.getProperty("java.awt.headless"));
    }

    public static ChildModel getModel(int id) throws ExtensionException {
        if (myModels.containsKey(id)) {
            return myModels.get(id);
        } else {
            throw new ExtensionException("There is no model with ID " + id);
        }
    }

    public static int castToId(Object id) throws ExtensionException {
        if (id instanceof Number) {
            return ((Number) id).intValue();
        } else {
            throw new ExtensionException("Expected a model ID but got: " + id);
        }
    }


    @Override
    public void unload(ExtensionManager arg0) throws ExtensionException {
        try {
            reset();
        } catch (HaltException e) {
            // we can ignore this
        }
    }


    private static String getModelPath(ExtensionContext ctx, String basePath) throws ExtensionException {
        try {
            return ctx.attachCurrentDirectory(basePath);
        } catch (MalformedURLException e) {
            throw new ExtensionException(e);
        }
    }

    public static class LoadHeadlessModel extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    // we take in int[] {number, string}
                    new int[] { Syntax.StringType()});
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // saving current modelCounter as that will be the hash table key to the
            // model we are making
            // make a new LevelsModel
            String modelURL = getModelPath((ExtensionContext) context, args[0].getString());
            HeadlessChildModel aModel;
            try {
                aModel = new HeadlessChildModel(context.getAgent().world(), modelURL, modelCounter);
            } catch (IOException e) {
                throw new ExtensionException ("There was no .nlogo file at the path: \"" + modelURL + "\"");
            } catch (CompilerException e) {
                throw new ExtensionException (modelURL + " did not compile properly. There is probably something wrong " +
                        "with its code. Exception said" + e.getMessage());
            }
            updateChildModelSpeed(aModel);
            myModels.put(modelCounter, aModel);
            // add to models counter
            modelCounter++;
        }
    }

    public static class LoadGUIModel extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    // we take in int[] {number, string}
                    new int[] { Syntax.StringType()});
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // Get the path for the model
            String modelURL = getModelPath((ExtensionContext) context, args[0].getString());
            GUIChildModel aModel;
            try {
                aModel = new GUIChildModel(context.getAgent().world(), modelURL, modelCounter);
                updateChildModelSpeed(aModel);
                // add it to models
                myModels.put(modelCounter, aModel);
                // add to models counter
                modelCounter ++;
            } catch (InterruptedException e) {
                throw new HaltException(false);
            } catch (InvocationTargetException e) {
                throw new ExtensionException("Loading " + modelURL + " failed with this message: " + e.getMessage());
            }
        }
    }

    public static void reset() throws ExtensionException, HaltException {
        modelCounter = 0;

        for (ChildModel model : myModels.values()){
            model.kill();
        }
        myModels.clear();
    }

    public static class Reset extends DefaultCommand {
        public void perform(Argument args[], Context context)
                throws org.nlogo.api.LogoException, ExtensionException {
            reset();
        }
    }

    private static ChildModel[] toModelList(Argument arg) throws LogoException, ExtensionException {
        Object obj = arg.get();
        if (obj instanceof Double) {
            return new ChildModel[] { getModel(arg.getIntValue()) };
        } else if (obj instanceof LogoList) {
            LogoList idList = arg.getList();
            ChildModel[] models = new ChildModel[idList.size()];
            int i = 0;
            for (Object modelIdObj : arg.getList()) {
                models[i] = getModel(castToId(modelIdObj));
                i++;
            }
            return models;
        } else {
            throw new ExtensionException("Expected a number or list");
        }
    }

    public static class Ask extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    new int[]{Syntax.NumberType() | Syntax.ListType(),
                            Syntax.CommandTaskType() | Syntax.StringType(),
                            Syntax.RepeatableType() | Syntax.WildcardType()},
                    2);
        }
        public void perform(Argument[] args, Context context) throws LogoException, ExtensionException {
            org.nlogo.nvm.Context nvmContext = ((ExtensionContext) context).nvmContext();
            Object command = args[1].get();
            Object[] actuals = getActuals(args, 2);
            for (ChildModel model : toModelList(args[0])) {
                if (command instanceof String) {
                    model.ask(nvmContext, (String) command, actuals);
                } else if (command instanceof CommandTask) {
                    model.ask(nvmContext, (CommandTask) command, actuals);
                } else {
                    throw new ExtensionException("You must give ls:ask a command task or string to run");
                }
            }
        }
    }

    public static class Of extends DefaultReporter {
        @Override
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    Syntax.ReporterTaskType() | Syntax.StringType(), // Code
                    new int[]{
                            Syntax.NumberType() | Syntax.ListType(), // Model(s)
                    },
                    Syntax.WildcardType() | Syntax.RepeatableType(),
                    org.nlogo.api.Syntax.NormalPrecedence() + 1,
                    true
            );
        }
        public Object report(Argument args[], Context context) throws LogoException, ExtensionException {
            LogoListBuilder results = new LogoListBuilder();
            org.nlogo.nvm.Context nvmContext = ((ExtensionContext) context).nvmContext();
            Object reporter = args[0].get();
            Object[] actuals = getActuals(args, 2);
            for (ChildModel model : toModelList(args[1])){
                if (reporter instanceof String) {
                    results.add(model.of(nvmContext, (String) reporter, actuals));
                } else if (reporter instanceof ReporterTask) {
                    results.add(model.of(nvmContext, (ReporterTask) reporter, actuals));
                }
            }
            LogoList returnValue = results.toLogoList();
            return args[1].get() instanceof Double ? returnValue.first() : returnValue;
        }
    }

    public static class HierarchicalAsk extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    new int[]{Syntax.ListType(), Syntax.StringType()});
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            LogoList list = args[0].getList();
            String cmd = args[1].getString();
            int modelNum = ((Double) list.first()).intValue();
            ChildModel aModel;
            aModel = getModel(modelNum);
            list = list.butFirst();
            String modelCommand = "";

            if (list.size() > 1){
                // need to reinsert escape chars
                cmd = cmd.replace("\"", "\\\"");
                // this currently doesn't work because it does this for the first and last
                // quotation marks too - which it should not.
                modelCommand = "ls:ask-descendant " + org.nlogo.api.Dump.logoObject(list) + " \"" + cmd + "\"";
            }

            // if it is exactly 1 that means we are at the parent of the model that we want
            // to ask to do something, so we just get the parent to ask its child
            if (list.size() == 1){
                // get the child model
                double childModelNumber = (Double) list.first();
                int childModelno = (int)childModelNumber;
                cmd = cmd.replace("\"", "\\\"");
                modelCommand = "ls:ask " +  childModelno + " \""+ cmd + "\"";
            }
            // then call command
            aModel.command(modelCommand);

        }

    }


    public static class HierarchicalOf extends DefaultReporter {
        public Syntax getSyntax(){
            return Syntax.reporterSyntax(
                    Syntax.StringType(),
                    new int[]{ Syntax.ListType() },
                    Syntax.WildcardType(),
                    org.nlogo.api.Syntax.NormalPrecedence() + 1,
                    true
            );

        }


        @Override
        public Object report(Argument[] args, Context arg1)
                throws ExtensionException, LogoException {
            // TODO Auto-generated method stub
            // get model number from args
            LogoList list = args[1].getList();
            // get the command
            String reporter = args[0].getString();
            // get the model
            double modelNumber = (Double) list.first();
            int modelNum = (int)modelNumber;
            ChildModel aModel;
            if (myModels.containsKey(modelNum)){
                aModel = myModels.get(modelNum);
            }
            else {
                throw new ExtensionException("The model with id " + modelNum + " did not exist.");
            }
            // then remove the model from the list
            list = list.butFirst();
            // Command string
            String modelCommand = "";

            // if the list is longer than one, we need to go deeper in the hierarchy
            if (list.size() > 1){
                // need to reinsert escape chars
                reporter = reporter.replace("\"", "\\\"");
                // this currently doesn't work because it does this for the first and last
                // quotation marks too - which it should not.
                modelCommand = "ls:of-descendant " + org.nlogo.api.Dump.logoObject(list) + " \"" + reporter + "\"";
            }

            // if it is exactly 1 that means we are at the parent of the model that we want
            // to ask to do something, so we just get the parent to ask its child
            if (list.size() == 1){
                // get the child model
                double childModelNumber = (Double) list.first();
                int childModelNum = (int)childModelNumber;
                reporter = reporter.replace("\"", "\\\"");
                modelCommand = "\"" + reporter + "\" ls:of " + childModelNum;
            }

            // then call command
            return aModel.report(modelCommand);

        }

    }


    public static class CloseModel extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    new int[] { Syntax.NumberType() });
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            closeModel((int) args[0].getDoubleValue());
        }
    }

    public static void closeModel(int modelNumber) throws ExtensionException {
        getModel(modelNumber).kill();
        myModels.remove(modelNumber);
    }

    public static class UpdateView extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    new int[]{Syntax.NumberType()});
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();
            // find the model. if it exists, update graphics
            if (getModel(modelNumber) instanceof HeadlessChildModel){
                HeadlessChildModel aModel = (HeadlessChildModel) getModel(modelNumber);
                aModel.updateView();
            }
        }
    }

    public static class Show extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    new int[] { Syntax.NumberType() });
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();
            // find the model. if it exists, run the command
            getModel(modelNumber).show();
        }
    }

    public static class Hide extends DefaultCommand {
        public Syntax getSyntax() {
            return Syntax.commandSyntax(
                    new int[]{Syntax.NumberType()});
        }

        public void perform(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();
            // find the model. if it exists, run the command
            getModel(modelNumber).hide();
        }
    }


    // this returns the path of the model
    public static class ModelName extends DefaultReporter{
        public Syntax getSyntax(){
            return Syntax.reporterSyntax(new int[] {Syntax.NumberType()},
                    Syntax.StringType());

        }
        public Object report(Argument[] args, Context context) throws ExtensionException, LogoException {
            int modelNumber = args[0].getIntValue();
            return getModel(modelNumber).getName();
        }

    }

    // this returns the path of the model
    public static class ModelPath extends DefaultReporter{
        public Syntax getSyntax(){
            return Syntax.reporterSyntax(new int[] {Syntax.NumberType()},
                    Syntax.StringType());

        }
        public Object report(Argument[] args, Context context) throws ExtensionException, LogoException {
            return getModel(args[0].getIntValue()).getPath();

        }

    }

    /*
     * This primitive returns the last created model number
     */
    public static class LastModel extends DefaultReporter {
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    // no parameters
                    new int[] {},
                    // and return a number
                    Syntax.NumberType());
        }

        public Double report(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {

            return (double) modelCounter - 1;

        }
    }

    public static class ModelExists extends DefaultReporter {
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    // we take in int[] {modelNumber, varName}
                    new int[] { Syntax.NumberType() },
                    // and return a number
                    Syntax.BooleanType());
        }

        public Object report(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();

            // find the model. if it exists, update graphics
            return myModels.containsKey(modelNumber);

        }
    }


    public static class BreedsOwns extends DefaultReporter {
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    // we take in int[] {modelNumber, varName}
                    new int[] { Syntax.NumberType() },
                    // and return a number
                    Syntax.ListType());
        }

        public Object report(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();

            // find the model. if it exists, get all breeds + owns
            if(myModels.containsKey(modelNumber))
            {
                ChildModel theModel = myModels.get(modelNumber);
                return theModel.listBreedsOwns();

            }
            else{
                return false;
            }

        }
    }
    public static class ListBreeds extends DefaultReporter {
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    // we take in int[] {modelNumber, varName}
                    new int[] { Syntax.NumberType() },
                    // and return a number
                    Syntax.ListType());
        }

        public Object report(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();
            // find the model. if it exists, update graphics
            if(myModels.containsKey(modelNumber))
            {
                ChildModel theModel = myModels.get(modelNumber);
                return theModel.listBreedsOwns();
            }
            else{
                return false;
            }

        }
    }

    public static class Globals extends DefaultReporter {
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    // we take in int[] {modelNumber, varName}
                    new int[] { Syntax.NumberType() },
                    // and return a number
                    Syntax.ListType());
        }

        public Object report(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            // get model number from args
            int modelNumber = (int) args[0].getDoubleValue();
            // find the model. if it exists, update graphics
            if(myModels.containsKey(modelNumber))
            {
                ChildModel theModel = myModels.get(modelNumber);
                return theModel.listGlobals();
            }
            else{
                throw new ExtensionException("There is no model with ID " + modelNumber);

            }

        }
    }

    public static class AllModels extends DefaultReporter {
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(
                    new int[] {},
                    Syntax.ListType());
        }

        public Object report(Argument args[], Context context)
                throws ExtensionException, org.nlogo.api.LogoException {
            LogoListBuilder myLLB = new LogoListBuilder();

            for (Integer id :  myModels.keySet()) {
                myLLB.add((double) id);
            }
            return myLLB.toLogoList();
        }
    }

    public static class UsesLevelSpace extends DefaultReporter {
        @Override
        public Syntax getSyntax() {
            return Syntax.reporterSyntax(new int[] {Syntax.NumberType()}, Syntax.BooleanType());
        }

        @Override
        public Object report(Argument[] args, Context context) throws LogoException, ExtensionException {
            return getModel(args[0].getIntValue()).usesLevelsSpace();
        }
    }

    @Override
    public List<String> additionalJars() {
        return null;
    }

    @Override
    public void clearAll() {
        // We want to keep models between clear-alls, yes?
    }

    @Override
    public StringBuilder exportWorld() {
        // Not supported
        return new StringBuilder();
    }

    @Override
    public void importWorld(List<String[]> arg0, ExtensionManager arg1,
                            ImportErrorHandler arg2) throws ExtensionException {
        // Not supported
    }

    @Override
    public ExtensionObject readExtensionObject(ExtensionManager arg0,
                                               String arg1, String arg2) throws ExtensionException,
            CompilerException {
        // Not supported
        return null;
    }

    @Override
    public void runOnce(ExtensionManager arg0) throws ExtensionException {

    }

    void updateChildModelsSpeed(){
        for (ChildModel model : myModels.values()){
            updateChildModelSpeed(model);
        }
    }


    static void updateChildModelSpeed(ChildModel model){
        // If we're running tests, this should noop. So, we check if we've got a GUI.
        if (useGUI()) {
            double theSpeed = App.app().workspace().speedSliderPosition();
            model.setSpeed(theSpeed);
        }
    }

    static void haltChildModels( HashMap<Integer, ChildModel> models){
        // Iterate through child models
        // First stop the child model, then get its (potential) child models and
        // send them here too
        for (ChildModel aModel : models.values()){
            aModel.halt();
        }

    }

    public static Object[] getActuals(Argument[] args, int startIndex) throws LogoException, ExtensionException {
        Object[] actuals = new Object[args.length - startIndex];
        for(int i=startIndex; i < args.length; i++) {
            actuals[i - startIndex] = args[i].get();
        }
        return actuals;
    }
}
