export type JobPosting = {
  id: number
  title: string
  company: string
  description: string
  sourceUrl: string | null
  createdAt: string
  updatedAt: string
}

export type JobInput = {
  title: string
  company: string
  description: string
  sourceUrl: string | null
}
