package userFields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Value;

import userFields.CustomInterpreter.ArrayValue;
import userFields.CustomInterpreter.MultiDArray;
import userFields.CustomInterpreter.ReferenceValue;

/*
 *  Do not accurately support getFrames method as the goal of the analyzer is to analyze each individual branch case separately hence when merged the new value
 *  will override the old one instead of being the union value of the different scenarios
 *  As jsr is outdated, this analyzer does not add on the extended interpreter to tackle subroutines
 *  Each frame's reference value and array values despite pointing to the same object as some other frames in analysis should be independent of one another to preserve their state at the frame 
 */
public class CustomAnalyzer implements Opcodes {
	private final CustomInterpreter interpreter;
	/** The instructions of the currently analyzed method. */
	private InsnList insnList;
	/** The size of {@link #insnList}. */
	private int insnListSize;	
	/** The exception handlers of the currently analyzed method (one list per instruction index). */
	private List<TryCatchBlockNode>[] handlers;
	/** The execution stack frames of the currently analyzed method (one per instruction index). */
	private CustomFrame[] frames;
	/** The instructions that remain to process (one boolean per instruction index). */
	private boolean[] inInstructionsToProcess;
	/** The indices of the instructions that remain to process in the currently analyzed method. */
	private int[] instructionsToProcess;
	/** The number of instructions that remain to process in the currently analyzed method. */
	private int numInstructionsToProcess;
	/** After analyzing every branch the branch that has the greatest degree of user influence in terms of number of fields influenced */
	private Map<String, Value> finalControlledFields;

	public CustomAnalyzer(final CustomInterpreter interpreter) {
		this.interpreter = interpreter;
	}

	/**
	 * Analyzes the given method.
	 *
	 * @param owner the internal name of the class to which 'method' belongs.
	 * @param method the method to be analyzed.
	 * @return the symbolic state of the execution stack frame at each bytecode instruction of the
	 *     method. The size of the returned array is equal to the number of instructions (and labels)
	 *     of the method. A given frame is {@literal null} if and only if the corresponding
	 *     instruction cannot be reached (dead code).
	 * @throws AnalyzerException if a problem occurs during the analysis.
	 */
	@SuppressWarnings("unchecked")
	public CustomFrame[] analyze(final String owner, final MethodNode method) throws AnalyzerException {
		if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) {
			frames = new CustomFrame[0];
			return frames;
		}
		insnList = method.instructions;
		insnListSize = insnList.size();
		handlers = (List<TryCatchBlockNode>[]) new List<?>[insnListSize];
		frames = new CustomFrame[insnListSize];
		inInstructionsToProcess = new boolean[insnListSize];
		instructionsToProcess = new int[insnListSize];
		numInstructionsToProcess = 0;

		// For each exception handler, and each instruction within its range, record in 'handlers' the
		// fact that execution can flow from this instruction to the exception handler.
		for (int i = 0; i < method.tryCatchBlocks.size(); ++i) {
			TryCatchBlockNode tryCatchBlock = method.tryCatchBlocks.get(i);
			int startIndex = insnList.indexOf(tryCatchBlock.start);
			int endIndex = insnList.indexOf(tryCatchBlock.end);
			for (int j = startIndex; j < endIndex; ++j) {
				List<TryCatchBlockNode> insnHandlers = handlers[j];
				if (insnHandlers == null) {
					insnHandlers = new ArrayList<>();
					handlers[j] = insnHandlers;
				}
				insnHandlers.add(tryCatchBlock);
			}
		}

		// Initializes the data structures for the control flow analysis.
		CustomFrame currentFrame = computeInitialFrame(owner, method);
		merge(0, currentFrame);
		init(owner, method);

		// Control flow analysis.
		while (numInstructionsToProcess > 0) {
			// Get and remove one instruction from the list of instructions to process.
			int insnIndex = instructionsToProcess[--numInstructionsToProcess];
			CustomFrame oldFrame = frames[insnIndex];
//			if (owner.endsWith("Notification")) {
//				System.out.println(oldFrame);
//			}
			inInstructionsToProcess[insnIndex] = false;

			// Simulate the execution of this instruction.
			AbstractInsnNode insnNode = null;
			try {
				insnNode = method.instructions.get(insnIndex);
				int insnOpcode = insnNode.getOpcode();
				int insnType = insnNode.getType();

				if (insnType == AbstractInsnNode.LABEL
						|| insnType == AbstractInsnNode.LINE
						|| insnType == AbstractInsnNode.FRAME) {
					merge(insnIndex + 1, currentFrame.init(oldFrame));
					newControlFlowEdge(insnIndex, insnIndex + 1);
				} else {
					currentFrame.init(oldFrame).execute(insnNode, interpreter);

					if (insnNode instanceof JumpInsnNode) {
						JumpInsnNode jumpInsn = (JumpInsnNode) insnNode;
						if (insnOpcode != Opcodes.GOTO) {
							int[] insnIndexes = new int[2];
							currentFrame.initJumpTarget(insnOpcode, /* target = */ null);
							insnIndexes[0] = insnIndex + 1;
							newControlFlowEdge(insnIndex, insnIndex + 1);
							int jumpInsnIndex = insnList.indexOf(jumpInsn.label);
							insnIndexes[1] = jumpInsnIndex;
							currentFrame.initJumpTarget(insnOpcode, jumpInsn.label);
							jumpMerge(insnIndexes, currentFrame);
							newControlFlowEdge(insnIndex, jumpInsnIndex);
						} else {
							int jumpInsnIndex = insnList.indexOf(jumpInsn.label);
							currentFrame.initJumpTarget(insnOpcode, jumpInsn.label);
							merge(jumpInsnIndex, currentFrame);
							newControlFlowEdge(insnIndex, jumpInsnIndex);
						}			
					} else if (insnNode instanceof LookupSwitchInsnNode) {
						LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode) insnNode;
						int[] insnIndexes = new int[1 + lookupSwitchInsn.labels.size()];
						int targetInsnIndex = insnList.indexOf(lookupSwitchInsn.dflt);
						insnIndexes[0] = targetInsnIndex;
						currentFrame.initJumpTarget(insnOpcode, lookupSwitchInsn.dflt);
						newControlFlowEdge(insnIndex, targetInsnIndex);
						for (int i = 0; i < lookupSwitchInsn.labels.size(); ++i) {
							LabelNode label = lookupSwitchInsn.labels.get(i);
							targetInsnIndex = insnList.indexOf(label);
							currentFrame.initJumpTarget(insnOpcode, label);
							insnIndexes[i + 1] = targetInsnIndex;
							newControlFlowEdge(insnIndex, targetInsnIndex);
						}
						jumpMerge(insnIndexes, currentFrame);
					} else if (insnNode instanceof TableSwitchInsnNode) {
						TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode) insnNode;
						int[] insnIndexes = new int[1 + tableSwitchInsn.labels.size()];
						int targetInsnIndex = insnList.indexOf(tableSwitchInsn.dflt);
						insnIndexes[0] = targetInsnIndex;
						currentFrame.initJumpTarget(insnOpcode, tableSwitchInsn.dflt);
						newControlFlowEdge(insnIndex, targetInsnIndex);
						for (int i = 0; i < tableSwitchInsn.labels.size(); ++i) {
							LabelNode label = tableSwitchInsn.labels.get(i);
							currentFrame.initJumpTarget(insnOpcode, label);
							targetInsnIndex = insnList.indexOf(label);
							insnIndexes[1 + i] = targetInsnIndex;
							newControlFlowEdge(insnIndex, targetInsnIndex);
						}
						jumpMerge(insnIndexes, currentFrame);
					} else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
						merge(insnIndex + 1, currentFrame);
						newControlFlowEdge(insnIndex, insnIndex + 1);
					} else if (insnOpcode >= IRETURN && insnOpcode <= RETURN) {
						if (finalControlledFields == null)  
							finalControlledFields = interpreter.getUserControlledFields();
						else {
							Map<String, Value> newControlledFields = interpreter.getUserControlledFields();
							if (replaceControlledFields(newControlledFields)) 
								finalControlledFields = newControlledFields;
						}
					}
				}

				List<TryCatchBlockNode> insnHandlers = handlers[insnIndex];
				if (insnHandlers != null) {
					for (TryCatchBlockNode tryCatchBlock : insnHandlers) {
						Type catchType;
						if (tryCatchBlock.type == null) {
							catchType = Type.getObjectType("java/lang/Throwable");
						} else {
							catchType = Type.getObjectType(tryCatchBlock.type);
						}
						if (newControlFlowExceptionEdge(insnIndex, tryCatchBlock)) {
							CustomFrame handler = newExceptionFrame(oldFrame);
							handler.push(interpreter.newException(tryCatchBlock, handler, catchType));
							merge(insnList.indexOf(tryCatchBlock.handler), handler);
						}
					}
				}
			} catch (AnalyzerException e) {
				String ownerAndMethod = owner + " " + method.name + method.desc;
				throw new CustomAnalyzerException(e.node, "Error at instruction " + insnIndex + ": " + e.getMessage(), e, ownerAndMethod);
			}
		}

		return frames;
	}

	/**
	 * Computes the initial execution stack frame of the given method.
	 *
	 * @param owner the internal name of the class to which 'method' belongs.
	 * @param method the method to be analyzed.
	 * @return the initial execution stack frame of the 'method'.
	 */
	private CustomFrame computeInitialFrame(final String owner, final MethodNode method) {
		CustomFrame frame = newFrame(method.maxLocals, method.maxStack);
		int currentLocal = 0;
		boolean isInstanceMethod = (method.access & ACC_STATIC) == 0;
		if (isInstanceMethod) {
			Type ownerType = Type.getObjectType(owner);
			frame.setLocal(
					currentLocal, interpreter.newParameterValue(isInstanceMethod, currentLocal, ownerType));
			currentLocal++;
		}
		Type[] argumentTypes = Type.getArgumentTypes(method.desc);
		for (Type argumentType : argumentTypes) {
			frame.setLocal(
					currentLocal,
					interpreter.newParameterValue(isInstanceMethod, currentLocal, argumentType));
			currentLocal++;
			if (argumentType.getSize() == 2) {
				frame.setLocal(currentLocal, interpreter.newEmptyValue(currentLocal));
				currentLocal++;
			}
		}
		while (currentLocal < method.maxLocals) {
			frame.setLocal(currentLocal, interpreter.newEmptyValue(currentLocal));
			currentLocal++;
		}
		frame.setReturn(interpreter.newReturnTypeValue(Type.getReturnType(method.desc)));
		frame.setFields(interpreter.getUserControlledFieldsForFrame());
		return frame;
	}

	/**
	 * Returns the symbolic execution stack frame for each instruction of the last analyzed method.
	 *
	 * @return the symbolic state of the execution stack frame at each bytecode instruction of the
	 *     method. The size of the returned array is equal to the number of instructions (and labels)
	 *     of the method. A given frame is {@literal null} if the corresponding instruction cannot be
	 *     reached, or if an error occurred during the analysis of the method.
	 */
	public CustomFrame[] getFrames() {
		return frames;
	}

	public Map<String, Value> getFinalControlledFields() {
		return finalControlledFields;
	}

	/**
	 * Returns the exception handlers for the given instruction.
	 *
	 * @param insnIndex the index of an instruction of the last analyzed method.
	 * @return a list of {@link TryCatchBlockNode} objects.
	 */
	public List<TryCatchBlockNode> getHandlers(final int insnIndex) {
		return handlers[insnIndex];
	}

	/**
	 * Initializes this analyzer. This method is called just before the execution of control flow
	 * analysis loop in #analyze. The default implementation of this method does nothing.
	 *
	 * @param owner the internal name of the class to which the method belongs.
	 * @param method the method to be analyzed.
	 * @throws AnalyzerException if a problem occurs.
	 */
	protected void init(final String owner, final MethodNode method) throws AnalyzerException {
		// Nothing to do.
	}

	/**
	 * Constructs a new frame with the given size.
	 *
	 * @param numLocals the maximum number of local variables of the frame.
	 * @param numStack the maximum stack size of the frame.
	 * @return the created frame.
	 */
	protected CustomFrame newFrame(final int numLocals, final int numStack) {
		return new CustomFrame(numLocals, numStack);
	}

	/**
	 * Creates a control flow graph edge. The default implementation of this method does nothing. It
	 * can be overridden in order to construct the control flow graph of a method (this method is
	 * called by the {@link #analyze} method during its visit of the method's code).
	 *
	 * @param insnIndex an instruction index.
	 * @param successorIndex index of a successor instruction.
	 */
	protected void newControlFlowEdge(final int insnIndex, final int successorIndex) {
		// Nothing to do.
	}

	/**
	 * Creates a control flow graph edge corresponding to an exception handler. The default
	 * implementation of this method does nothing. It can be overridden in order to construct the
	 * control flow graph of a method (this method is called by the {@link #analyze} method during its
	 * visit of the method's code).
	 *
	 * @param insnIndex an instruction index.
	 * @param successorIndex index of a successor instruction.
	 * @return true if this edge must be considered in the data flow analysis performed by this
	 *     analyzer, or false otherwise. The default implementation of this method always returns
	 *     true.
	 */
	protected boolean newControlFlowExceptionEdge(final int insnIndex, final int successorIndex) {
		return true;
	}

	/**
	 * Creates a control flow graph edge corresponding to an exception handler. The default
	 * implementation of this method delegates to {@link #newControlFlowExceptionEdge(int, int)}. It
	 * can be overridden in order to construct the control flow graph of a method (this method is
	 * called by the {@link #analyze} method during its visit of the method's code).
	 *
	 * @param insnIndex an instruction index.
	 * @param tryCatchBlock TryCatchBlockNode corresponding to this edge.
	 * @return true if this edge must be considered in the data flow analysis performed by this
	 *     analyzer, or false otherwise. The default implementation of this method delegates to {@link
	 *     #newControlFlowExceptionEdge(int, int)}.
	 */
	protected boolean newControlFlowExceptionEdge(final int insnIndex, final TryCatchBlockNode tryCatchBlock) {
		return newControlFlowExceptionEdge(insnIndex, insnList.indexOf(tryCatchBlock.handler));
	}

	// -----------------------------------------------------------------------------------------------

	/**
	 * Merges the given frame and subroutine into the frame and subroutines at the given instruction
	 * index. If the frame or the subroutine at the given instruction index changes as a result of
	 * this merge, the instruction index is added to the list of instructions to process (if it is not
	 * already the case).
	 *
	 * @param insnIndex an instruction index.
	 * @param frame a frame. This frame is left unchanged by this method.
	 * @param subroutine a subroutine. This subroutine is left unchanged by this method.
	 * @throws AnalyzerException if the frames have incompatible sizes.
	 */
	private void merge(final int insnIndex, final CustomFrame frame) throws AnalyzerException {
		boolean changed;
		CustomFrame oldFrame = frames[insnIndex];
		if (oldFrame == null) {
			frames[insnIndex] = newFrame(frame);
			changed = true;
		} else {
			changed = oldFrame.merge(frame, interpreter);
		}

		if (changed && !inInstructionsToProcess[insnIndex]) {
			inInstructionsToProcess[insnIndex] = true;
			instructionsToProcess[numInstructionsToProcess++] = insnIndex;
		}
	}
	
	protected CustomFrame newFrame(final CustomFrame frame) {
		return new CustomFrame(frame);
	}
	
	protected CustomFrame newJumpFrame(final CustomFrame frame) {
		CustomFrame newframe = new CustomFrame(frame.getLocals(), frame.getMaxStackSize());
		List<Value> existingValues = new ArrayList<Value>(); // when copying the stack and local values with identical reference should maintain this relationship
		List<Value> newValues = new ArrayList<Value>();
		for (int i = 0; i < frame.getLocals(); i++) {
			Value local = frame.getLocal(i);
			newframe.setLocal(i, CustomFrame.copyStack(existingValues, newValues, local));
		}
		newframe.setStackSize(frame.getStackSize());
		for (int j = 0; j < frame.getStackSize(); j++) {
			Value stack = frame.getStack(j);
			newframe.setStack(j, CustomFrame.copyStack(existingValues, newValues, stack));
		}
		Map<String, Value> userControlledFields = new HashMap<>();
		interpreter.getUserControlledFieldsForFrame().forEach((field, val) -> {
			userControlledFields.put(field, CustomFrame.copyStack(existingValues, newValues, val));
		});
		newframe.setFields(userControlledFields);
		return newframe;
    }
	
	/*
	 * When insn jumps due to tryCatchBlock, the method is called to handle the resulting frame
	 */
	protected CustomFrame newExceptionFrame(final CustomFrame frame) {
		CustomFrame newframe = new CustomFrame(frame.getLocals(), frame.getMaxStackSize());
		List<Value> existingValues = new ArrayList<Value>(); // when copying the stack and local values with identical reference should maintain this relationship
		List<Value> newValues = new ArrayList<Value>();
		for (int i = 0; i < frame.getLocals(); i++) {
			Value local = frame.getLocal(i);
			newframe.setLocal(i, CustomFrame.copyStack(existingValues, newValues, local));
		}
		newframe.clearStack();
		Map<String, Value> userControlledFields = new HashMap<>();
		interpreter.getUserControlledFieldsForFrame().forEach((field, val) -> {
			userControlledFields.put(field, CustomFrame.copyStack(existingValues, newValues, val));
		});
		newframe.setFields(userControlledFields);
		return newframe;
	}
	
	// when there is branching each branch should have an independent snapshot of the user fields
	// assumes no aliasing between different fields
	private void jumpMerge(int[] insnIndexes, final CustomFrame frame) throws AnalyzerException {
		for (int i = 0; i < insnIndexes.length; i++) {
			boolean changed;
			int insnIndex = insnIndexes[i];
			CustomFrame oldFrame = frames[insnIndex];
			if (oldFrame == null) {
				if (i == 0) {
					frames[insnIndex] = newFrame(frame);
				} else
					frames[insnIndex] = newJumpFrame(frame);
				changed = true;
			} else {
				if (i == 0) {
					changed = oldFrame.merge(frame, interpreter);
				} else
					changed = oldFrame.mergeJumpFrame(frame, interpreter);
			}
			
			if (changed && !inInstructionsToProcess[insnIndex]) {
			    inInstructionsToProcess[insnIndex] = true;
			    instructionsToProcess[numInstructionsToProcess++] = insnIndex;
			}
		}
	}
	
	public static class CustomAnalyzerException extends AnalyzerException {
		private static final long serialVersionUID = 1L;

		public CustomAnalyzerException(final AbstractInsnNode insn, final String message, final Throwable cause, String method) {
			super(insn, method + " " + message, cause);
		}
	}
	
	public boolean replaceControlledFields(Map<String, Value> newControlledFields) {
		Iterator<Map.Entry<String, Value>> it = finalControlledFields.entrySet().iterator();
		int fieldCount1 = 0;
		int localTaintCount1 = 0;
		while (it.hasNext()) {
			Map.Entry<String, Value> field = (Map.Entry<String, Value>) it.next();
			Value fieldVal = field.getValue();
			if (!(fieldVal instanceof UserValues)) {
				if (fieldVal instanceof ReferenceValue) {
					if (((ReferenceValue) fieldVal).getIsField())
						fieldCount1++;
					else 
						localTaintCount1++;
				} else if (fieldVal instanceof ArrayValue) {
					ArrayValue arr = (ArrayValue) fieldVal;
					if (arr.getType().equals(Type.getObjectType("java/lang/Object"))) {
						if (arr.getIsField())
							fieldCount1++;
						else
							localTaintCount1++;
					}
				} else {
					MultiDArray multiArr = (MultiDArray) fieldVal;
					if (multiArr.getType().equals(Type.getObjectType("java/lang/Object"))) {
						if (multiArr.getIsField())
							fieldCount1++;
						else 
							localTaintCount1++;
					}
				}
			}
		}
		Iterator<Map.Entry<String, Value>> it2 = newControlledFields.entrySet().iterator();
		int fieldCount2 = 0;
		int localTaintCount2 = 0;
		while (it2.hasNext()) {
			Map.Entry<String, Value> field = (Map.Entry<String, Value>) it2.next();
			Value fieldVal = field.getValue();
			if (!(fieldVal instanceof UserValues)) {
				if (fieldVal instanceof ReferenceValue) {
					if (((ReferenceValue) fieldVal).getIsField())
						fieldCount2++;
					else 
						localTaintCount2++;
				} else if (fieldVal instanceof ArrayValue) {
					ArrayValue arr = (ArrayValue) fieldVal;
					if (arr.getType().equals(Type.getObjectType("java/lang/Object"))) {
						if (arr.getIsField())
							fieldCount2++;
						else
							localTaintCount2++;
					}
				} else {
					MultiDArray multiArr = (MultiDArray) fieldVal;
					if (multiArr.getType().equals(Type.getObjectType("java/lang/Object"))) {
						if (multiArr.getIsField())
							fieldCount2++;
						else 
							localTaintCount2++;
					}
				}
			}
		}
		if (fieldCount1 == fieldCount2) {
			if (localTaintCount1 == localTaintCount2) {
				if (finalControlledFields.size() >= newControlledFields.size())
					return false;
				else 
					return true;
			} else if (localTaintCount1 >= localTaintCount2)
				return false;
			else 
				return true;
		} else if (fieldCount1 > fieldCount2)
			return false;
		else 
			return true;
	}
}
