ALTER TABLE checklist_templates
    ADD COLUMN name_vi VARCHAR(200),
    ADD COLUMN description_vi VARCHAR(2000);

ALTER TABLE checklist_items
    ADD COLUMN section_name_vi VARCHAR(160),
    ADD COLUMN prompt_vi VARCHAR(1000),
    ADD COLUMN guidance_vi VARCHAR(2000);

-- Existing checklist text is retained unchanged. These nullable fields allow a future Vietnamese
-- catalog to be filled without translating administrator-authored content or blocking deployment.
-- Localized reads use the English columns whenever a Vietnamese value is absent or blank.
