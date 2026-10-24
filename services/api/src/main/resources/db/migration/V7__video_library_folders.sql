ALTER TABLE videos ADD COLUMN folder_name VARCHAR(80);
CREATE INDEX videos_workspace_folder_created_idx ON videos(workspace_id, folder_name, created_at DESC);
