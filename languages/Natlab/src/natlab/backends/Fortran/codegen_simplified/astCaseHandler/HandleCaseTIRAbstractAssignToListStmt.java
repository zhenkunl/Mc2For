package natlab.backends.Fortran.codegen_simplified.astCaseHandler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import natlab.tame.valueanalysis.value.Args;
import natlab.tame.tir.*;
import natlab.tame.builtin.*;
import natlab.tame.builtin.shapeprop.HasShapePropagationInfo;
import natlab.tame.builtin.shapeprop.ShapePropTool;
import natlab.tame.valueanalysis.components.constant.*;
import natlab.tame.valueanalysis.components.shape.*;
import natlab.tame.valueanalysis.basicmatrix.BasicMatrixValueFactory;
import natlab.backends.Fortran.codegen_simplified.*;
import natlab.backends.Fortran.codegen_simplified.FortranAST_simplified.*;

public class HandleCaseTIRAbstractAssignToListStmt {
	static boolean Debug = false;
	static BasicMatrixValueFactory basicMatrixValueFactory = new BasicMatrixValueFactory();
	
	/**
	 * AbstractAssignToListStmt: Statement ::= [RuntimeCheck] Variable* Expression;
	 * for each statement, currently, we need to insert two check:
	 * 1. for rhs, constant folding check;
	 * 2. for lhs, variable allocation check.
	 */
	public Statement getFortran(FortranCodeASTGenerator fcg, TIRAbstractAssignToListStmt node) {
		if (Debug) System.out.println("in an abstractAssignToList statement");
		String indent = new String();
		for (int i=0; i<fcg.indentNum; i++) {
			indent = indent + fcg.indent;
		}
		/*
		 * TODO Change to support built-ins with multiple args.
		 * Currently it handles general case built-ins with one or two args only.
		 */
		int RHSCaseNumber = getRHSCaseNumber(fcg, node);
		String RHSFortranOperator = getRHSMappingFortranOperator(fcg, node);
		String Operand1 = getOperand1(node);
		String Operand2 = getOperand2(node);
		String ArgsListasString;
		ArrayList<String> arguments = new ArrayList<String>();
		arguments = getArgsList(node);

		RuntimeAllocate rta = new RuntimeAllocate();
		@SuppressWarnings("rawtypes")
		List<Shape> currentShape = getCurrentShape(fcg, node, node.getRHS().getVarName(), arguments);
		
		switch (RHSCaseNumber) {
		case 1:
			BinaryExpr binExpr = new BinaryExpr();
			binExpr.setIndent(indent);
			for (ast.Name name : node.getTargets().asNameList()) {
				Variable var = new Variable();
				/*
				 * if an input argument of the function is on the LHS of an assignment stmt, 
				 * we assume that this input argument maybe modified.
				 */
				if (fcg.isInSubroutine && fcg.inArgs.contains(name.getID())) {
					if (Debug) System.out.println(
							"subroutine's input "+name.getID()+" has been modified!");
					fcg.inputHasChanged.add(name.getID());
					var.setName(name.getID()+"_copy");
				}
				else var.setName(name.getID());
				binExpr.addVariable(var);
			}
			/*
			 * insert constant folding check.
			 */
			if (fcg.getMatrixValue(Operand1).hasConstant() 
					&& !fcg.inArgs.contains(Operand1) 
					&& fcg.tamerTmpVar.contains(Operand1)) {
				Constant c = fcg.getMatrixValue(Operand1).getConstant();
				binExpr.setOperand1(c.toString());
			}
			else {
				if (fcg.inputHasChanged.contains(Operand1))	binExpr.setOperand1(Operand1+"_copy");
				else binExpr.setOperand1(Operand1);	
			}
			if (fcg.getMatrixValue(Operand2).hasConstant() 
					&& !fcg.inArgs.contains(Operand2) 
					&& fcg.tamerTmpVar.contains(Operand2)) {
				Constant c = fcg.getMatrixValue(Operand2).getConstant();
				binExpr.setOperand2(c.toString());
			}
			else {
				if (fcg.inputHasChanged.contains(Operand2))	binExpr.setOperand2(Operand2+"_copy");
				else binExpr.setOperand2(Operand2);
			}
			binExpr.setOperator(RHSFortranOperator);
			if (!fcg.getMatrixValue(node.getTargetName().getID()).getShape().isConstant()) {
				StringBuffer tmpBuf = new StringBuffer();
				tmpBuf.append(indent+"!insert runtime allocation.\n");
				tmpBuf.append(indent+"IF (ALLOCATED("+node.getTargetName().getID()+")) THEN\n");
				tmpBuf.append(indent+"   DEALLOCATE("+node.getTargetName().getID()+");\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"ELSE\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"END IF\n");
				rta.setBlock(tmpBuf.toString());
			}
			binExpr.setRuntimeAllocate(rta);
			return binExpr;
		case 2:
			UnaryExpr unExpr = new UnaryExpr();
			unExpr.setIndent(indent);
			for (ast.Name name : node.getTargets().asNameList()) {
				Variable var = new Variable();
				/*
				 * if an input argument of the function is on the LHS of an assignment stmt, 
				 * we assume that this input argument maybe modified.
				 */
				if (fcg.isInSubroutine && fcg.inArgs.contains(name.getID())) {
					if (Debug) System.out.println(
							"subroutine's input "+name.getID()+" has been modified!");
					fcg.inputHasChanged.add(name.getID());
					var.setName(name.getID()+"_copy");
				}
				else var.setName(name.getID());
				unExpr.addVariable(var);
			}
			/*
			 * insert constant folding check.
			 */
			if (fcg.getMatrixValue(Operand1).hasConstant() 
					&& !fcg.inArgs.contains(Operand1) 
					&& fcg.tamerTmpVar.contains(Operand1)) {
				Constant c = fcg.getMatrixValue(Operand1).getConstant();
				unExpr.setOperand(c.toString());
			}
			else {
				if (fcg.inputHasChanged.contains(Operand1))	unExpr.setOperand(Operand1+"_copy");
				else unExpr.setOperand(Operand1);
			}
			unExpr.setOperator(RHSFortranOperator);
			if (!fcg.getMatrixValue(node.getTargetName().getID()).getShape().isConstant()) {
				StringBuffer tmpBuf = new StringBuffer();
				tmpBuf.append(indent+"!insert runtime allocation.\n");
				tmpBuf.append(indent+"IF (ALLOCATED("+node.getTargetName().getID()+")) THEN\n");
				tmpBuf.append(indent+"   DEALLOCATE("+node.getTargetName().getID()+");\n");
				tmpBuf.append(indent+"ELSE\n");	
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"ELSE\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"END IF\n");
				rta.setBlock(tmpBuf.toString());
			}
			unExpr.setRuntimeAllocate(rta);
			return unExpr;
		case 3:
			DirectBuiltinExpr dirBuiltinExpr = new DirectBuiltinExpr();
			dirBuiltinExpr.setIndent(indent);
			for (ast.Name name : node.getTargets().asNameList()) {
				Variable var = new Variable();
				/*
				 * if an input argument of the function is on the LHS of an assignment stmt, 
				 * we assume that this input argument maybe modified.
				 */
				if (fcg.isInSubroutine && fcg.inArgs.contains(name.getID())) {
					if (Debug) System.out.println(
							"subroutine's input "+name.getID()+" has been modified!");
					fcg.inputHasChanged.add(name.getID());
					var.setName(name.getID()+"_copy");
				}
				else var.setName(name.getID());
				dirBuiltinExpr.addVariable(var);
			}
			/*
			 * insert constant folding check.
			 */
			for (int i=0;i<arguments.size();i++) {
				if (fcg.getMatrixValue(arguments.get(i)).hasConstant() 
						&& !fcg.inArgs.contains(arguments.get(i)) 
						&& fcg.tamerTmpVar.contains(arguments.get(i))) {
					Constant c = fcg.getMatrixValue(arguments.get(i)).getConstant();
					arguments.remove(i);
					arguments.add(i, c.toString());
				}
				else {
					if (fcg.inputHasChanged.contains(arguments.get(i))) {
						String ArgsNew = arguments.get(i)+"_copy";
						arguments.remove(i);
						arguments.add(i, ArgsNew);
					}
				}
			}
			ArgsListasString = getArgsListAsString(arguments);
			dirBuiltinExpr.setBuiltinFunc(RHSFortranOperator);
			dirBuiltinExpr.setArgsList(ArgsListasString);
			if (!fcg.getMatrixValue(node.getTargetName().getID()).getShape().isConstant()) {
				StringBuffer tmpBuf = new StringBuffer();
				tmpBuf.append(indent+"!insert runtime allocation.\n");
				tmpBuf.append(indent+"IF (ALLOCATED("+node.getTargetName().getID()+")) THEN\n");
				tmpBuf.append(indent+"   DEALLOCATE("+node.getTargetName().getID()+");\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"ELSE\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"END IF\n");
				rta.setBlock(tmpBuf.toString());
			}
			dirBuiltinExpr.setRuntimeAllocate(rta);
			return dirBuiltinExpr;
		case 4:
			NoDirectBuiltinExpr noDirBuiltinExpr = new NoDirectBuiltinExpr();
			/*
			 * insert indent, constant folding check and variable 
			 * allocation check in corresponding in-lined code.
			 */
			noDirBuiltinExpr = FortranCodeASTInliner.inline(fcg, node, currentShape);
			System.out.println(node.getTargetName().getID());
			if (!fcg.isCell(node.getTargetName().getID()) && fcg.hasSingleton(node.getTargetName().getID()) 
					&& !fcg.getMatrixValue(node.getTargetName().getID()).getShape().isConstant()) {
				StringBuffer tmpBuf = new StringBuffer();
				tmpBuf.append(indent+"!insert runtime allocation.\n");
				tmpBuf.append(indent+"IF (ALLOCATED("+node.getTargetName().getID()+")) THEN\n");
				tmpBuf.append(indent+"   DEALLOCATE("+node.getTargetName().getID()+");\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"ELSE\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"END IF\n");
				rta.setBlock(tmpBuf.toString());
			}
			noDirBuiltinExpr.setRuntimeAllocate(rta);
			return noDirBuiltinExpr;
		case 5:
			BuiltinConstantExpr builtinConst = new BuiltinConstantExpr();
			builtinConst.setIndent(indent);
			for (ast.Name name : node.getTargets().asNameList()) {
				Variable var = new Variable();
				/*
				 * if an input argument of the function is on the LHS of an assignment stmt, 
				 * we assume that this input argument maybe modified.
				 */
				if (fcg.isInSubroutine && fcg.inArgs.contains(name.getID())) {
					if (Debug) System.out.println(
							"subroutine's input "+name.getID()+" has been modified!");
					fcg.inputHasChanged.add(name.getID());
					var.setName(name.getID()+"_copy");
				}
				else var.setName(name.getID());
				builtinConst.addVariable(var);
			}
			builtinConst.setBuiltinFunc(RHSFortranOperator);
			if (!fcg.getMatrixValue(node.getTargetName().getID()).getShape().isConstant()) {
				StringBuffer tmpBuf = new StringBuffer();
				tmpBuf.append(indent+"!insert runtime allocation.\n");
				tmpBuf.append(indent+"IF (ALLOCATED("+node.getTargetName().getID()+")) THEN\n");
				tmpBuf.append(indent+"   DEALLOCATE("+node.getTargetName().getID()+");\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"ELSE\n");
				tmpBuf.append(indent+"   ALLOCATE("+node.getTargetName().getID()+"(");
				for (int i=0; i<currentShape.get(0).getDimensions().size(); i++) {
					if (currentShape.get(0).isConstant()) 
						tmpBuf.append(currentShape.get(0).getDimensions().get(i));
					else tmpBuf.append("int("+currentShape.get(0).getDimensions().get(i)+")");
					if (i<currentShape.get(0).getDimensions().size()-1) tmpBuf.append(",");
				}
				tmpBuf.append("));\n");
				tmpBuf.append(indent+"END IF\n");
				rta.setBlock(tmpBuf.toString());
			}
			builtinConst.setRuntimeAllocate(rta);
			return builtinConst;
		case 6:
			IOOperationExpr ioExpr = new IOOperationExpr();
			ioExpr.setIndent(indent);
			/*
			 * insert constant folding check.
			 */
			for (int i=0;i<arguments.size();i++) {
				if (!fcg.isCell(arguments.get(i)) && fcg.getMatrixValue(arguments.get(i)).hasConstant() 
						&& !fcg.inArgs.contains(arguments.get(i)) 
						&& fcg.tamerTmpVar.contains(arguments.get(i))) {
					Constant c = fcg.getMatrixValue(arguments.get(i)).getConstant();
					arguments.remove(i);
					if (c instanceof CharConstant) arguments.add(i, "'"+c+"'");
					else arguments.add(i, c.toString());
				}
				else {
					if (fcg.inputHasChanged.contains(arguments.get(i))) {
						String ArgsNew = arguments.get(i)+"_copy";
						arguments.remove(i);
						arguments.add(i, ArgsNew);
					}
				}
			}
			ArgsListasString = getArgsListAsString(arguments);
			ioExpr.setArgsList(ArgsListasString);
			ioExpr.setIOOperator(RHSFortranOperator);
			return ioExpr;
		default:
			/*
			 * deal with user defined functions, apparently, there 
			 * is no corresponding Fortran built-in function for this. 
			 * mapping all the user defined functions to subroutines 
			 * in Fortran. Subroutines in Fortran is more similar to 
			 * functions in MATLAB than functions in Fortran.
			 */
			Subroutines subroutine = new Subroutines();
			subroutine.setIndent(indent);
			ArrayList<String> outputArgsList = new ArrayList<String>();
			for (ast.Name name : node.getTargets().asNameList()) {

				/*
				 * if an input argument of the function is on the LHS of an assignment stmt, 
				 * we assume that this input argument maybe modified.
				 */
				if (fcg.isInSubroutine && fcg.inArgs.contains(name.getID())) {
					if (Debug) System.out.println("subroutine's input "+name.getID()
							+" has been modified!");
					fcg.inputHasChanged.add(name.getID());
					outputArgsList.add(name.getID()+"_copy");
				}
				else outputArgsList.add(name.getID());
			}
			/*
			 * insert constant folding check.
			 */
			for (int i=0;i<arguments.size();i++) {
				if (fcg.getMatrixValue(arguments.get(i)).hasConstant() 
						&& !fcg.inArgs.contains(arguments.get(i)) 
						&& fcg.tamerTmpVar.contains(arguments.get(i))) {
					Constant c = fcg.getMatrixValue(arguments.get(i)).getConstant();
					arguments.remove(i);
					arguments.add(i, c.toString());
				}
				else {
					if (fcg.inputHasChanged.contains(arguments.get(i))) {
						String ArgsNew = arguments.get(i)+"_copy";
						arguments.remove(i);
						arguments.add(i, ArgsNew);
					}			
				}
			}
			ArgsListasString = getArgsListAsString(arguments);
			String funcName;
			funcName = node.getRHS().getVarName();
			subroutine.setFuncName(funcName);
			subroutine.setInputArgsList(ArgsListasString);
			subroutine.setOutputArgsList(outputArgsList.toString().replace("[", "")
					.replace("]", ""));
			return subroutine;
		}
	}
	/***************************************helper methods****************************************/
	public static int getRHSCaseNumber(FortranCodeASTGenerator fcg, 
			TIRAbstractAssignToListStmt node) {
		String RHSMatlabOperator;
		RHSMatlabOperator = node.getRHS().getVarName();
		if (fcg.FortranMapping.isFortranBinOperator(RHSMatlabOperator)) return 1;
		else if (fcg.FortranMapping.isFortranUnOperator(RHSMatlabOperator)) return 2;
		else if (fcg.FortranMapping.isFortranDirectBuiltin(RHSMatlabOperator)) return 3;
		else if(fcg.FortranMapping.isFortranNoDirectBuiltin(RHSMatlabOperator))	return 4;
		else if(fcg.FortranMapping.isBuiltinConst(RHSMatlabOperator)) return 5;
		else if(fcg.FortranMapping.isFortranIOOperation(RHSMatlabOperator)) return 6;
		else return 7; // "user defined function";
	}
	
	public static String getOperand1(TIRAbstractAssignToListStmt node) {
		if (node.getRHS().getChild(1).getNumChild()>=1)
			return node.getRHS().getChild(1).getChild(0).getNodeString();
		else return "";
	}
	
	public static String getOperand2(TIRAbstractAssignToListStmt node) {
		if (node.getRHS().getChild(1).getNumChild() >= 2) 
			return node.getRHS().getChild(1).getChild(1).getNodeString();
		else return "";
	}
	
	public static String getRHSMappingFortranOperator(FortranCodeASTGenerator fcg, 
			TIRAbstractAssignToListStmt node) {
		String RHSFortranOperator;
		String RHSMatlabOperator;
		RHSMatlabOperator = node.getRHS().getVarName();
		if (fcg.FortranMapping.isFortranBinOperator(RHSMatlabOperator)) {
			RHSFortranOperator = fcg.FortranMapping.getFortranBinOpMapping(RHSMatlabOperator);
		}
		else if (fcg.FortranMapping.isFortranUnOperator(RHSMatlabOperator)) {
			RHSFortranOperator = fcg.FortranMapping.getFortranUnOpMapping(RHSMatlabOperator);
		}
		else if (fcg.FortranMapping.isFortranDirectBuiltin(RHSMatlabOperator)) {
			RHSFortranOperator = fcg.FortranMapping.getFortranDirectBuiltinMapping(RHSMatlabOperator);
		}
		else if (fcg.FortranMapping.isFortranNoDirectBuiltin(RHSMatlabOperator)) {
			RHSFortranOperator = RHSMatlabOperator;
		}
		else if (fcg.FortranMapping.isBuiltinConst(RHSMatlabOperator)) {
			RHSFortranOperator = fcg.FortranMapping.getFortranBuiltinConstMapping(RHSMatlabOperator);
		}
		else if (fcg.FortranMapping.isFortranIOOperation(RHSMatlabOperator)) {
			RHSFortranOperator = fcg.FortranMapping.getFortranIOOperationMapping(RHSMatlabOperator);
		}
		else RHSFortranOperator = "user defined function, no mapping, sorry.";
		return RHSFortranOperator;
	}
	
	public static ArrayList<String> getArgsList(TIRAbstractAssignToListStmt node) {
		ArrayList<String> Args = new ArrayList<String>();
		int numArgs = node.getRHS().getChild(1).getNumChild();
		for (int i=0;i<numArgs;i++) {
			Args.add(node.getRHS().getChild(1).getChild(i).getNodeString());
		}
		return Args;
	}

	public static String getArgsListAsString(ArrayList<String> Args) {
		String prefix ="";
		String argListasString="";
		for (String arg : Args) {
			argListasString = argListasString+prefix+arg;
			prefix=", ";
		}
		return argListasString;
	}

	public String makeFortranStringLiteral(String StrLit) {		
		if (StrLit.charAt(0)=='\'' && StrLit.charAt(StrLit.length()-1)=='\'') {			
			return "\""+StrLit.subSequence(1, StrLit.length()-1)+"\"";		
		}
		return StrLit;
	}

	/**
	 * used for runtime allocating variable's size.
	 */
	@SuppressWarnings({"unchecked","rawtypes"})
	public static List<Shape> getCurrentShape(FortranCodeASTGenerator fcg, 
			TIRAbstractAssignToListStmt node, String functionName, ArrayList<String> arguments) {
		int nargout = node.getTargets().size();
		Builtin builtin = Builtin.getInstance(functionName);
		ShapePropTool shapePropTool = new ShapePropTool();
		/*
		 * allocate with exact shape.
		 */
		LinkedList argumentList1 = new LinkedList();
		for (String arg1 : arguments) {
			argumentList1.add(fcg.getMatrixValue(arg1));
		}
		Args arg1 = Args.newInstance(null, nargout, argumentList1);
		List<Shape> result1 = shapePropTool.matchByValues(
				((HasShapePropagationInfo)builtin).getShapePropagationInfo()
				, arg1);
		if (result1.get(0).isConstant()) return result1;
		/*
		 * allocate with symbolic information.
		 */
		LinkedList argumentList2 = new LinkedList();
		for (String arg2 : arguments) {
			argumentList2.add(basicMatrixValueFactory
					.newMatrixValueFromInputShape(arg2, null, "1*1"));
		}
		Args arg2 = Args.newInstance(null, nargout, argumentList2);
		List<Shape> result2 = shapePropTool.matchByValues(
				((HasShapePropagationInfo)builtin).getShapePropagationInfo()
				, arg2);
		return result2;
	}
}
