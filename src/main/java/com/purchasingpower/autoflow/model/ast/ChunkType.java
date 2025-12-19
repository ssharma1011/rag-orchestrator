package com.purchasingpower.autoflow.model.ast;

/**
 * Represents the type of code chunk for AST-based indexing.
 * Each chunk type requires different parsing and embedding strategies.
 */
public enum ChunkType {
    /**
     * Top-level class definition (includes class-level metadata)
     */
    CLASS,

    /**
     * Method definition (includes method body and signature)
     */
    METHOD,

    /**
     * Interface definition
     */
    INTERFACE,

    /**
     * Enum definition
     */
    ENUM,

    /**
     * Class field/member variable
     */
    FIELD,

    /**
     * Custom annotation definition
     */
    ANNOTATION,

    /**
     * Constructor (special type of method)
     */
    CONSTRUCTOR
}