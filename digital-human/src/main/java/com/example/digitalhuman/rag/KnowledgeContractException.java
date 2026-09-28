package com.example.digitalhuman.rag;

/** 元数据契约被破坏（缺字段、越界命中）时抛出，属于「不该发生但要立刻喊出来」的一类。 */
public class KnowledgeContractException extends RuntimeException {

    public KnowledgeContractException(String message) {
        super(message);
    }
}
