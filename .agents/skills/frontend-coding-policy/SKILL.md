---
name: frontend-coding-policy
description: Mandatory frontend coding policies for required fields, validation, error handling, date formatting, and file upload examples.
---

# Skill: Frontend Coding Policy

## ℹ️ Objective
This document outlines the standard coding practices and UI guidelines for the frontend application, specifically targeting form handling, validation, error reporting, date formats, and file uploads. All agents and engineers must strictly adhere to these policies when generating or modifying React components, custom JSON-render nodes, or page logic.

---

## 🏛️ Core Architectural Principles (核心開發與架構觀念)

All frontend components and business logic must adhere to the following architectural concepts to maintain clean, robust, and scalable code:

### A. Strongly-Typed SOLID Principles
- **TypeScript as a Strongly-Typed OOP/Interface-Driven Language**: Although React is fundamentally functional in UI presentation, TypeScript gives us powerful Object-Oriented capabilities (interfaces, polymorphism, generic constraints). All utilities, API structures, and shared interfaces must enforce strong typing.
- **Adherence to SOLID**:
  - **S (Single Responsibility)**: Keep components focused on a single task. Render templates must only render UI; business behaviors belong to registered controller files.
  - **O (Open/Closed)**: Components and helpers should be open for extension (e.g., extending the global `api` client options with `postForm`) but closed for modification.
  - **D (Dependency Inversion)**: Depend upon abstractions, not concrete implementations (e.g., page layers communicate with APIs and state through general interfaces and registered behaviors).

### B. MVC Separation of Concerns (Responsibility Boundary)
While React is not inherently an MVC framework, our project's architecture enforces a highly clean Separation of Concerns that aligns perfectly with **MVC (Model-View-Controller)** principles:
1. **View (`json-render`)**: The custom `json-render` components serve as pure, declarative render engines. They consume schemas (e.g., `*.render-schema.json`) and registries to render UI layouts. They are entirely decoupled from business logic and maintain zero state side-effects.
2. **Controller (`*.page.tsx`)**: The page files act as Controllers. They register static behaviors (`registerBehavior`), validate forms, catch user interactions, trigger data changes (`store.set`), coordinate API network requests, and manage modal transitions.
3. **Model (`React Query` & `JSONUI Store`)**: The local client-side state store and global query cache represent the Model, maintaining structural integrity, data consistency, and validation schemas.

---

## 📋 Coding Policies & Rules

### 1. Required Field Indicator (必填欄位紅色星號註記)
- **Rule**: If an input field has `required: true` (or contains standard validation checks specifying it is required), the field label **MUST** render a red asterisk (`*`) immediately next to the text.
- **Component Design**: 
  - Standard inputs (e.g., `Input`, `Select`, `Textarea`, `DatePicker`) must inspect their validation schema or `required` prop.
  - Render the label with:
    ```tsx
    <Label htmlFor={id}>
      {label}
      {required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
    </Label>
    ```

---

### 2. Pre-submit Validation (送出 API 前的基本驗證)
- **Rule**: Before executing a mutation (such as a POST/PUT/PATCH behavior via `executeBehavior` or manual submit), the frontend **MUST** perform local form-state validation.
- **Rules to Validate**:
    - **Presence**: All required fields must have non-empty, non-null values.
    - **Format**: Basic pattern checks (e.g., Date strings, numeric constraints) must be satisfied.
- **UX Flow**:
    1. Validate active values in the form state store.
    2. If any validation fails, **abort the submission** and prevent API calls.
    3. Inform the user with specific validation error messages (either inline or via descriptive error toasts).

---

### 3. Detailed Toast Error Messages (Toast 錯誤訊息詳細化)
- **Rule**: Never show a cryptic status code like "Error 400" or "Error 500" in toasts. The toast message **MUST** display clear, descriptive error details telling the user what actually went wrong.
- **Implementation Pattern**:
  - Catch responses from API calls. If the server returns a detailed payload (e.g. `{ message: "學期名稱重複", code: ... }`), parse and render that exact text.
  - Fallback cleanly to a readable default if the body is unparseable:
    ```typescript
    const errorMessage = error?.response?.data?.message 
      || error?.message 
      || "系統發生未知錯誤，請稍後再試。";
    toast.error(`儲存失敗：${errorMessage}`);
    ```

---

### 4. ISO 8601 Date Format Standard (日期格式 ISO 8601)
- **Rule**: Date exchange format and display guidelines must strictly use **ISO 8601** (`YYYY-MM-DD` or full timestamp `YYYY-MM-DDTHH:mm:ss.sssZ`).
- **Guidelines**:
  - Any input field representing a date must explicitly set its placeholder to specify the format (e.g., `YYYY-MM-DD`).
  - Inputs must parse the input string and serialize it to a valid ISO 8601 date string before syncing with the state store.

---

### 5. File Upload Template / Example (檔案上傳範例與範本提供)
- **Rule**: If a page or modal prompts the user to upload a file in a specific schema (e.g., a student roster CSV or grade import Excel sheet), a **downloadable template** or **visual sample table** **MUST** be displayed directly next to the upload control.
- **UI Design**:
  - Provide a clear download button/link:
    ```tsx
    <div className="text-xs text-muted-foreground mt-1">
      支援格式：CSV。請依循{" "}
      <a href="/templates/student-import-template.csv" className="text-primary underline" download>
        範例範本
      </a>{" "}
      的格式上傳。
    </div>
    ```
  - Alternatively, render a small, read-only visual table showing the headers and one mock row to guide the user.

---

## 🛠️ Verification Checklist
Before submitting a pull request or marking a ticket complete:
- [ ] Do all required input components show a red `*` next to their label?
- [ ] Does clicking "Submit/Save" check for empty required fields and format mismatches before hitting the API?
- [ ] Are toast error handlers passing the actual error `message` from the backend?
- [ ] Do date pickers or date text fields specify `YYYY-MM-DD` as a placeholder?
- [ ] Does every file uploader have an accompanying "Download Template" link or structure diagram?
